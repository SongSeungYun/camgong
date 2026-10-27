package com.camgong.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCancelled;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyChanged;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCompleted;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyFailed;

/**
 * 네 사건을 커밋 뒤(AFTER_COMMIT)에만 받아 알림 행으로 바꿔 전용 실행기에 넘긴다. 롤백된 변경에는 알림이 없고, 원 요청은 알림 생성을
 * 기다리지 않는다. 대상은 이벤트의 recipientIds 뿐이다(공구나 참여 테이블을 다시 읽지 않는다 — 사건 시점 참여자가 이미 담겨 있다).
 * 알림 내용은 제목·종류·바뀐 항목 이름·시각뿐이다(다른 회원 정보 금지, FR-004). 여기서 던진 예외가 원 요청에 영향을 주지 않도록 모두 잡는다.
 */
@Component
class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationWriter writer;
    private final Executor executor;
    private final Clock clock;

    NotificationEventListener(NotificationWriter writer, @Qualifier("notificationExecutor") Executor executor, Clock clock) {
        this.writer = writer;
        this.executor = executor;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GroupBuyCompleted e) {
        dispatch(e.eventId(), () -> rows(e.eventId(), e.recipientIds(), NotificationType.GROUPBUY_COMPLETED, e.groupBuyId(),
                e.groupBuyTitle(), null));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GroupBuyFailed e) {
        dispatch(e.eventId(), () -> rows(e.eventId(), e.recipientIds(), NotificationType.GROUPBUY_FAILED, e.groupBuyId(),
                e.groupBuyTitle(), null));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GroupBuyChanged e) {
        dispatch(e.eventId(), () -> rows(e.eventId(), e.recipientIds(), NotificationType.GROUPBUY_CHANGED, e.groupBuyId(),
                e.groupBuyTitle(), String.join(",", e.changedFields())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GroupBuyCancelled e) {
        dispatch(e.eventId(), () -> rows(e.eventId(), e.recipientIds(), NotificationType.GROUPBUY_CANCELLED, e.groupBuyId(),
                e.groupBuyTitle(), null));
    }

    private List<NotificationRow> rows(String eventId, List<Long> recipients, NotificationType type, Long groupBuyId, String title,
            String changedFields) {
        Instant now = Instant.now(clock);
        return recipients.stream().distinct()
                .map(r -> new NotificationRow(eventId, r, type, groupBuyId, title, changedFields, now)).toList();
    }

    /**
     * 이미 커밋된 원 요청을 절대 실패시키지 않는다: 행 만들기(recipientIds 가 null 인 이벤트 등)부터 실행기 제출까지 모든 예외를 삼키고
     * WARN 만 남긴다(유실로 취급, 허용된 한계).
     */
    private void dispatch(String eventId, java.util.function.Supplier<List<NotificationRow>> rowsSupplier) {
        List<NotificationRow> rows;
        try {
            rows = rowsSupplier.get();
        } catch (RuntimeException e) {
            log.warn("notification-dropped eventId={} reason=invalid-event {}", eventId, e.getClass().getSimpleName());
            return;
        }
        if (rows.isEmpty()) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    writer.write(rows);
                } catch (RuntimeException e) {
                    log.warn("notification-write-failed eventId={} recipients={} reason={}", eventId, rows.size(),
                            e.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException e) {
            // 큐가 가득 차 거부됐다(AbortPolicy): 원 요청을 느리게 하지 않고 유실로 취급한다(허용된 한계).
            log.warn("notification-dropped eventId={} recipients={} reason={}", eventId, rows.size(), e.getClass().getSimpleName());
        }
    }
}

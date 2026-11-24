package com.camgong.groupbuy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCancelled;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyChanged;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCompleted;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyFailed;

// ponytail: 커밋 후 유실 가능, 알림 기능에서 전달 수단 연결·신뢰성 필요하면 아웃박스로
// ponytail: 커밋 직후 서버가 멈추면 모집 실패 이벤트가 유실될 수 있음, 알림 기능이 소비
/** 받는 회원은 수만 남긴다(012 결정 5): 컨테이너 로그는 크기로만 돌아 1년 넘게 남을 수 있고, id 목록은 공구를 지운 뒤에도 참여 기록이 된다. */
@Component
class GroupBuyEventLogger {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyEventLogger.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GroupBuyChanged e) {
        log.info("GroupBuyChanged groupBuyId={} recipients={} fields={}", e.groupBuyId(), e.recipientIds().size(),
                e.changedFields());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GroupBuyCancelled e) {
        log.info("GroupBuyCancelled groupBuyId={} recipients={}", e.groupBuyId(), e.recipientIds().size());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GroupBuyCompleted e) {
        log.info("GroupBuyCompleted groupBuyId={} recipients={}", e.groupBuyId(), e.recipientIds().size());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GroupBuyFailed e) {
        log.info("GroupBuyFailed groupBuyId={} recipients={}", e.groupBuyId(), e.recipientIds().size());
    }
}

package com.camgong.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;

import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCancelled;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyChanged;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCompleted;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyFailed;

/** DB 없이: 이벤트 → 알림 행 변환 규칙 */
class NotificationEventListenerTest {

    private final List<List<NotificationRow>> written = new ArrayList<>();
    private final NotificationWriter writer = new NotificationWriter(null, null, null) {
        @Override
        void write(List<NotificationRow> rows) {
            written.add(rows);
        }
    };
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneOffset.UTC);
    private final NotificationEventListener listener = new NotificationEventListener(writer, Runnable::run, clock);

    @Test
    void anEmptyRecipientListWritesNothing() {
        listener.on(new GroupBuyCompleted("e1", 1L, "t", List.of()));
        listener.on(new GroupBuyCancelled("e2", 1L, "t", List.of()));
        assertThat(written).isEmpty();
    }

    @Test
    void onlyTheEventsRecipientsGetRowsWithTheSnapshotTitle() {
        listener.on(new GroupBuyFailed("e1", 7L, "스냅샷 제목", List.of(1L, 2L, 3L, 2L)));       // 중복 id 는 한 번만

        assertThat(written).hasSize(1);
        assertThat(written.get(0)).extracting(NotificationRow::recipientId).containsExactly(1L, 2L, 3L);
        assertThat(written.get(0)).allSatisfy(r -> {
            assertThat(r.type()).isEqualTo(NotificationType.GROUPBUY_FAILED);
            assertThat(r.groupBuyTitle()).isEqualTo("스냅샷 제목");
            assertThat(r.eventId()).isEqualTo("e1");
            assertThat(r.changedFields()).isNull();
            assertThat(r.createdAt()).isEqualTo(Instant.parse("2026-10-27T00:00:00Z"));
        });
    }

    @Test
    void changedFieldsAreStoredAsACommaSeparatedListAndTwoEditsKeepTheirOwnEventIds() {
        listener.on(new GroupBuyChanged("a", 1L, "t", List.of(2L), List.of("unitPrice", "deadline")));
        listener.on(new GroupBuyChanged("b", 1L, "t", List.of(2L), List.of("title")));

        assertThat(written).hasSize(2);
        assertThat(written.get(0).get(0).changedFields()).isEqualTo("unitPrice,deadline");
        assertThat(written.get(1).get(0).changedFields()).isEqualTo("title");
        assertThat(written.get(0).get(0).eventId()).isNotEqualTo(written.get(1).get(0).eventId());
    }

    @Test
    void aRejectedExecutionIsSwallowedSoTheRequestIsNeverAffected() {
        var rejecting = new NotificationEventListener(writer, r -> {
            throw new RejectedExecutionException("queue full");
        }, clock);

        rejecting.on(new GroupBuyCompleted("e1", 1L, "t", List.of(1L)));      // 예외가 밖으로 나오지 않는다

        assertThat(written).isEmpty();
    }

    @Test
    void aWriterFailureIsSwallowedToo() {
        var failing = new NotificationEventListener(new NotificationWriter(null, null, null) {
            @Override
            void write(List<NotificationRow> rows) {
                throw new IllegalStateException("db down");
            }
        }, Runnable::run, clock);

        failing.on(new GroupBuyCompleted("e1", 1L, "t", List.of(1L)));
    }

    @Test
    void anEventWithNullRecipientsOrFieldsNeverThrowsIntoTheCommittedRequest() {
        listener.on(new GroupBuyCompleted("e1", 1L, "t", null));
        listener.on(new GroupBuyFailed("e2", 1L, "t", null));
        listener.on(new GroupBuyCancelled("e3", 1L, "t", null));
        listener.on(new GroupBuyChanged("e4", 1L, "t", null, List.of("title")));
        listener.on(new GroupBuyChanged("e5", 1L, "t", List.of(2L), null));

        assertThat(written).isEmpty();
    }
}

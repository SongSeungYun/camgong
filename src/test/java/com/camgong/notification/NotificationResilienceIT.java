package com.camgong.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCompleted;

/** quickstart 시나리오 16 (SC-007): 한 수신자의 삽입 실패가 나머지 수신자를 막지 않고, 재시도에서 중복이 생기지 않는다. */
@ExtendWith(OutputCaptureExtension.class)
class NotificationResilienceIT extends NotificationTestBase {

    @MockitoSpyBean
    NotificationWriter writer;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    PlatformTransactionManager txManager;

    private void publish(GroupBuyCompleted event) {
        new TransactionTemplate(txManager).executeWithoutResult(s -> publisher.publishEvent(event));
    }

    @Test
    void aFailingChunkIsIsolatedRowByRowAndATransientRowFailureIsRetriedWithoutDuplicates() {
        doAnswer(inv -> { throw new IllegalStateException("청크 실패"); }).when(writer).batchChunk(any());
        AtomicInteger attemptsFor3 = new AtomicInteger();
        doAnswer(inv -> {
            NotificationRow row = inv.getArgument(0);
            if (row.recipientId() == 3L && attemptsFor3.incrementAndGet() <= 2) {
                throw new IllegalStateException("일시적 실패");
            }
            return inv.callRealMethod();
        }).when(writer).insertOne(any());

        publish(new GroupBuyCompleted(UUID.randomUUID().toString(), 1L, "t", List.of(1L, 2L, 3L)));

        assertThat(recipientsOf("GROUPBUY_COMPLETED")).containsExactly(1L, 2L, 3L);     // 모두 생겼고 3 번도 정확히 1건
        assertThat(attemptsFor3.get()).isEqualTo(3);                                    // 처음 + 재시도 2번
        assertThat(notificationCount()).isEqualTo(3);
    }

    @Test
    void aRowThatKeepsFailingIsGivenUpWithAWarningAndDoesNotBlockTheOthers(CapturedOutput output) {
        doAnswer(inv -> { throw new IllegalStateException("청크 실패"); }).when(writer).batchChunk(any());
        doAnswer(inv -> {
            NotificationRow row = inv.getArgument(0);
            if (row.recipientId() == 3L) {
                throw new IllegalStateException("계속 실패");
            }
            return inv.callRealMethod();
        }).when(writer).insertOne(any());
        String eventId = UUID.randomUUID().toString();

        publish(new GroupBuyCompleted(eventId, 1L, "t", List.of(1L, 2L, 3L, 4L)));

        assertThat(recipientsOf("GROUPBUY_COMPLETED")).containsExactly(1L, 2L, 4L);
        assertThat(output.getAll()).contains("notification-failed eventId=" + eventId + " recipientId=3");
        assertThat(output.getAll()).as("이름·이메일은 남기지 않는다").doesNotContain("@school.ac.kr");
    }

    @Test
    void onlyTheFailingChunkFallsBackToSingleRowsAndTheOtherChunksStayBatched() {
        seedMembers(100, 1099);
        AtomicInteger batchCalls = new AtomicInteger();
        AtomicInteger singleInserts = new AtomicInteger();
        doAnswer(inv -> {
            if (batchCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("첫 청크만 실패");
            }
            return inv.callRealMethod();
        }).when(writer).batchChunk(any());
        doAnswer(inv -> {
            singleInserts.incrementAndGet();
            return inv.callRealMethod();
        }).when(writer).insertOne(any());
        List<Long> everyone = new java.util.ArrayList<>();
        for (long m = 100; m < 1100; m++) {
            everyone.add(m);
        }

        publish(new GroupBuyCompleted(UUID.randomUUID().toString(), 1L, "t", everyone));

        assertThat(notificationCount()).isEqualTo(1000);
        assertThat(batchCalls.get()).isEqualTo(2);                 // 500 + 500
        assertThat(singleInserts.get()).isEqualTo(500);            // 실패한 청크의 500건만 단건으로
    }
}

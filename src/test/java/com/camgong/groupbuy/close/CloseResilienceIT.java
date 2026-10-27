package com.camgong.groupbuy.close;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.camgong.groupbuy.GroupBuyEvents.GroupBuyFailed;

/** quickstart 시나리오 14, 15 */
@ExtendWith(OutputCaptureExtension.class)
@Import(CloseResilienceIT.FailingListenerConfig.class)
class CloseResilienceIT extends CloseTestBase {

    /** 커밋 직전에 예외를 던져 특정 공구의 확정 트랜잭션을 롤백시키는 테스트용 리스너. */
    static class FailingListener {
        final Set<Long> failFor = ConcurrentHashMap.newKeySet();

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        void on(GroupBuyFailed e) {
            if (failFor.contains(e.groupBuyId())) {
                throw new IllegalStateException("테스트가 일으킨 실패 groupBuyId=" + e.groupBuyId());
            }
        }
    }

    @TestConfiguration
    static class FailingListenerConfig {
        @Bean
        FailingListener failingListener() {
            return new FailingListener();
        }
    }

    @Autowired
    FailingListener failing;

    @AfterEach
    void reset() {
        failing.failFor.clear();
    }

    @Test
    void oneFailingGroupBuyDoesNotBlockTheOthersAndIsRetriedWithoutDuplicates(CapturedOutput output) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(seed("공구 " + i, "OPEN", T.minusSeconds(100 - i), 24, 9));
        }
        long broken = ids.get(3);
        failing.failFor.add(broken);
        clock.set(T);

        CloseStats first = closeService.closeExpired();

        assertThat(first.closed()).isEqualTo(9);
        assertThat(first.failed()).isEqualTo(1);
        assertThat(statusOf(broken)).isEqualTo("OPEN");                              // 롤백되어 그대로
        assertThat(ids.stream().filter(id -> id != broken).map(this::statusOf)).containsOnly("FAILED");
        assertThat(output.getAll()).contains("close-failed groupBuyId=" + broken);
        assertThat(output.getAll()).as("WARN 에 스택 트레이스가 함께 남는다").contains("테스트가 일으킨 실패 groupBuyId=" + broken)
                .contains("\tat com.camgong");
        assertThat(output.getAll()).contains("close-run closed=9 skipped=0 failed=1");
        assertThat(events.failed()).hasSize(9);                                       // 롤백된 건의 이벤트는 나가지 않는다

        failing.failFor.clear();                                                      // 다음 실행에서 성공
        CloseStats second = closeService.closeExpired();

        assertThat(second.closed()).isEqualTo(1);
        assertThat(statusOf(broken)).isEqualTo("FAILED");
        assertThat(events.failed()).hasSize(10);
        assertThat(events.failed().stream().map(GroupBuyFailed::groupBuyId)).doesNotHaveDuplicates();
    }

    @Test
    void aBacklogIsClearedInDeadlineOrderAfterTheSchedulerWasOff() {
        List<Long> byDeadline = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            // 마감 시각이 id 순서와 다르게 흩어지도록 섞는다
            byDeadline.add(seed("밀린 공구 " + i, "OPEN", T.minusSeconds(10_000 - (i * 37L % 50) * 100), 24, 9));
        }
        List<Long> expectedOrder = byDeadline.stream()
                .sorted(Comparator.comparing(id -> jdbc.queryForObject("SELECT deadline FROM group_buy WHERE id = ?",
                        java.time.LocalDateTime.class, id)))
                .toList();
        clock.set(T);

        CloseStats stats = closeService.closeExpired();

        assertThat(stats.closed()).isEqualTo(50);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_buy WHERE status = 'FAILED'", Integer.class)).isEqualTo(50);
        assertThat(events.failed().stream().map(GroupBuyFailed::groupBuyId).toList()).as("마감이 이른 순").isEqualTo(expectedOrder);
    }

    @Test
    void moreCandidatesThanOneBatchAreAllProcessedAcrossRounds() {
        // 기본 batch-size 100 보다 많은 250건
        for (int i = 0; i < 250; i++) {
            seed("대량 " + i, "OPEN", T.minusSeconds(1_000 + i), 24, 9);
        }
        clock.set(T);

        CloseStats stats = closeService.closeExpired();

        assertThat(stats.closed()).isEqualTo(250);
        assertThat(stats.rounds()).isEqualTo(3);
    }

    /** 계속 실패하는 공구가 한 배치 이상 앞쪽에 있어도 뒤쪽 후보가 굶지 않는다. */
    @Test
    void persistentlyFailingGroupBuysDoNotStarveLaterCandidates() {
        int batch = 100;                                    // 기본 batch-size
        List<Long> poison = new ArrayList<>();
        for (int i = 0; i < batch + 5; i++) {
            long id = seed("독 " + i, "OPEN", T.minusSeconds(10_000 - i), 24, 9);
            poison.add(id);
            failing.failFor.add(id);
        }
        List<Long> good = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            good.add(seed("정상 " + i, "OPEN", T.minusSeconds(100 - i), 24, 9));
        }
        clock.set(T);

        CloseStats stats = closeService.closeExpired();

        assertThat(stats.failed()).isEqualTo(batch + 5);
        assertThat(stats.closed()).isEqualTo(5);
        assertThat(good.stream().map(this::statusOf)).containsOnly("FAILED");
        assertThat(poison.stream().map(this::statusOf)).containsOnly("OPEN");
    }
}

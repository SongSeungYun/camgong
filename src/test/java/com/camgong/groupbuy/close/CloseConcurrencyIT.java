package com.camgong.groupbuy.close;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.camgong.groupbuy.GroupBuyEvents.GroupBuyFailed;
import com.camgong.support.Concurrent;

/** quickstart 시나리오 8, 9, 10 — 헌법 IV 필수: 스케줄러 중복 실행 방지. 반복 횟수는 -Drace.repeat (CI 기본 20, 수동 1,000). */
class CloseConcurrencyIT extends CloseTestBase {

    private static final int REPEAT = Integer.getInteger("race.repeat", 20);

    /** 마감이 지난 미충족 공구를 n 건 한꺼번에 넣는다(개설자 참여 행 포함). */
    private List<Long> seedExpired(int n) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(seed("만료 " + i, "OPEN", T.minusSeconds(60 + i), 24, 9));
        }
        return ids;
    }

    @Test
    void fiveWorkersAtOnceCloseEveryGroupBuyExactlyOnceWithOneEventEach() throws Exception {
        for (int round = 0; round < REPEAT; round++) {
            jdbc.update("DELETE FROM participation");
            jdbc.update("DELETE FROM group_buy");
            events.clear();
            List<Long> ids = seedExpired(100);
            clock.set(T);

            List<Callable<CloseStats>> workers = new ArrayList<>();
            for (int w = 0; w < 5; w++) {
                workers.add(closeService::closeExpired);
            }
            List<CloseStats> stats = Concurrent.all(workers);

            assertThat(stats).as("round %d", round).extracting(CloseStats::failed).containsOnly(0);
            assertThat(stats.stream().mapToInt(CloseStats::closed).sum()).as("확정 합계").isEqualTo(100);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_buy WHERE status = 'FAILED'", Integer.class)).isEqualTo(100);
            assertThat(wronglyFailed(clock.instant())).isEmpty();
            List<Long> eventIds = events.failed().stream().map(GroupBuyFailed::groupBuyId).toList();
            assertThat(eventIds).as("공구마다 이벤트 정확히 1건").hasSize(100).doesNotHaveDuplicates()
                    .containsExactlyInAnyOrderElementsOf(ids);
            assertThat(events.failed().stream().map(GroupBuyFailed::eventId).collect(Collectors.toSet())).hasSize(100);
        }
    }

    @Test
    void overlappingRunsNeverProcessTheSameGroupBuyTwice() throws Exception {
        List<Long> ids = seedExpired(200);
        clock.set(T);

        // 한 실행이 끝나기 전에 다음 실행이 시작되는 경우를 흉내 낸다: 두 실행이 같은 후보 목록을 동시에 본다.
        List<CloseStats> stats = Concurrent.all(List.of(closeService::closeExpired, closeService::closeExpired,
                closeService::closeExpired));

        assertThat(stats.stream().mapToInt(CloseStats::closed).sum()).isEqualTo(200);
        assertThat(events.failed()).hasSize(200);
        assertThat(events.failed().stream().map(GroupBuyFailed::groupBuyId)).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void runningAgainOnAlreadyFailedGroupBuysDoesNothingAndPublishesNothing() {
        seedExpired(10);
        clock.set(T);
        assertThat(closeService.closeExpired().closed()).isEqualTo(10);
        events.clear();
        int version = jdbc.queryForObject("SELECT SUM(version) FROM group_buy", Integer.class);

        CloseStats again = closeService.closeExpired();

        assertThat(again.closed()).isZero();
        assertThat(again.rounds()).isZero();
        assertThat(events.failed()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT SUM(version) FROM group_buy", Integer.class)).isEqualTo(version);
    }

    @Test
    void closeOneSkipsWhatAnotherTransactionHoldsLocked() throws Exception {
        long id = seedExpired(1).get(0);
        clock.set(T);
        var tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        List<GroupBuyCloser.Outcome> inner = new ArrayList<>();

        tx.executeWithoutResult(s -> {
            // 다른 워커가 이 공구를 선점한 상태를 만든다
            jdbc.queryForList("SELECT id FROM group_buy WHERE id = ? FOR UPDATE", Long.class, id);
            try {
                inner.add(Concurrent.all(List.<Callable<GroupBuyCloser.Outcome>>of(
                        () -> closer.closeOne(id, clock.instant().minusSeconds(1), clock.instant()))).get(0));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(inner).containsExactly(GroupBuyCloser.Outcome.SKIPPED);        // 기다리지 않고 건너뛴다
        assertThat(statusOf(id)).isEqualTo("OPEN");
    }

    @org.springframework.beans.factory.annotation.Autowired
    GroupBuyCloser closer;

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.transaction.PlatformTransactionManager txManager;
}

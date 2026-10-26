package com.camgong.groupbuy;

import static com.camgong.support.GroupBuyBodies.valid;
import static com.camgong.support.GroupBuyBodies.with;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 18. 참여×수정·취소 동시성은 참여 기능에서 검증한다. */
class ModifyConcurrencyIT extends IntegrationTestBase {

    /** 모든 작업이 준비된 뒤 동시에 출발시키고 응답 상태 코드를 돌려준다. */
    private List<Integer> runTogether(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();
        return statuses;
    }

    @Test
    void updateRacingCancelEndsCancelledAndAnyLateUpdateIsNotFound() throws Exception {
        for (int round = 0; round < 5; round++) {
            jdbc.update("DELETE FROM participation");
            jdbc.update("DELETE FROM group_buy");
            jdbc.update("DELETE FROM create_guard");
            long id = openGroupBuy();

            List<Integer> statuses = runTogether(List.of(
                    () -> updateAs(ORGANIZER, id, with(valid(), "unitPrice", 1500)).andReturn().getResponse().getStatus(),
                    () -> cancelAs(ORGANIZER, id).andReturn().getResponse().getStatus()));

            // 수정이 먼저 락을 잡으면 200 뒤에 취소 204, 취소가 먼저면 수정은 404. 어느 쪽이든 최종은 CANCELLED.
            assertThat(statuses.get(1)).isEqualTo(204);
            assertThat(statuses.get(0)).isIn(200, 404);
            assertThat(jdbc.queryForObject("SELECT status FROM group_buy WHERE id = ?", String.class, id))
                    .isEqualTo("CANCELLED");
        }
    }

    @Test
    void racingOrganizerQuantityIncreasesNeverOversellAndCompleteExactlyOnce() throws Exception {
        long id = openGroupBuy(); // 총 24, 개설자 9
        seedParticipant(id, PARTICIPANT, 6); // secured 15

        // 6 + 18 = 24 만 정확히 채운다. 20 은 항상 초과(400 또는 이미 완료되어 409).
        List<Integer> quantities = List.of(10, 12, 14, 16, 18, 20);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int q : quantities) {
            tasks.add(() -> updateAs(ORGANIZER, id, with(valid(), "organizerQuantity", q))
                    .andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = runTogether(tasks);

        assertThat(statuses.get(quantities.indexOf(18))).isEqualTo(200);
        assertThat(statuses.get(quantities.indexOf(20))).isIn(400, 409);

        int secured = jdbc.queryForObject("SELECT secured_quantity FROM group_buy WHERE id = ?", Integer.class, id);
        int sum = jdbc.queryForObject("SELECT SUM(quantity) FROM participation WHERE group_buy_id = ?",
                Integer.class, id);
        assertThat(secured).isEqualTo(sum).isLessThanOrEqualTo(24);
        assertThat(secured).isEqualTo(24);
        assertThat(jdbc.queryForObject("SELECT status FROM group_buy WHERE id = ?", String.class, id))
                .isEqualTo("COMPLETED");
        assertThat(events.completed()).hasSize(1);
    }
}

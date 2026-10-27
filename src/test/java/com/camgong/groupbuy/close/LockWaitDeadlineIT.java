package com.camgong.groupbuy.close;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.camgong.support.GroupBuyBodies;

/**
 * spec FR-009: 요청이 처리되는 순간의 시각으로 마감을 판정한다. 요청이 마감 직전에 도착해 다른 트랜잭션이 쥔 공구 행 락을 기다리는 동안
 * 마감이 지나면, 락을 받은 뒤에는 마감 뒤 요청으로 거부해야 한다(도착 시각으로 통과시키면 마감 연장이 확정 대상 공구를 되살린다).
 */
class LockWaitDeadlineIT extends CloseTestBase {

    @Autowired
    PlatformTransactionManager txManager;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @org.junit.jupiter.api.AfterEach
    void shutDown() {
        pool.shutdownNow();
    }

    /** 공구 행 락을 쥐고 있다가, 요청이 락에서 막힌 뒤 시계를 마감 뒤로 보내고 락을 놓는다. 요청의 응답 상태를 돌려준다. */
    private int requestThatWaitsPastTheDeadline(long id, java.util.concurrent.Callable<Integer> request) throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = pool.submit(() -> new TransactionTemplate(txManager).executeWithoutResult(s -> {
            jdbc.queryForList("SELECT id FROM group_buy WHERE id = ? FOR UPDATE", Long.class, id);
            holding.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();

        try {
            clock.set(T.minusMillis(100));                          // 마감 0.1초 전에 도착
            Future<Integer> req = pool.submit(request);
            Thread.sleep(500);                                      // 요청이 락에서 막힐 시간
            assertThat(req.isDone()).as("요청은 다른 트랜잭션이 쥔 락 때문에 아직 끝나지 않았다").isFalse();
            clock.set(T.plusSeconds(1));                            // 기다리는 사이 마감이 지났다
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            return req.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();                                    // 단언이 실패해도 락을 놓아 다음 테스트를 막지 않는다
        }
    }

    @Test
    void anExtensionThatWaitedForTheLockPastTheDeadlineIsRefused() throws Exception {
        long id = openUnderfilled("락 대기 연장");
        var body = GroupBuyBodies.with(GroupBuyBodies.valid(T.plus(Duration.ofDays(3))), "title", "락 대기 연장");

        int status = requestThatWaitsPastTheDeadline(id, () -> updateAs(ORGANIZER, id, body).andReturn().getResponse().getStatus());

        assertThat(status).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT deadline FROM group_buy WHERE id = ?", java.time.LocalDateTime.class, id))
                .isEqualTo(java.time.LocalDateTime.ofInstant(T, java.time.ZoneOffset.UTC));      // 마감은 연장되지 않았다
    }

    @Test
    void aCancelThatWaitedForTheLockPastTheDeadlineIsRefused() throws Exception {
        long id = openUnderfilled("락 대기 취소");

        int status = requestThatWaitsPastTheDeadline(id, () -> cancelAs(ORGANIZER, id).andReturn().getResponse().getStatus());

        assertThat(status).isEqualTo(409);
        assertThat(statusOf(id)).isEqualTo("OPEN");
    }

    @Test
    void aJoinThatWaitedForTheLockPastTheDeadlineIsRefused() throws Exception {
        seedMembers(4, 4);
        long id = openUnderfilled("락 대기 참여");

        int status = requestThatWaitsPastTheDeadline(id, () -> mockMvc.perform(post("/api/groupbuys/" + id + "/participations")
                .with(as(4)).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1,\"expectedUnitPrice\":1200}"))
                .andReturn().getResponse().getStatus());

        assertThat(status).isEqualTo(409);
        assertThat(participationRows(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT secured_quantity FROM group_buy WHERE id = ?", Integer.class, id)).isEqualTo(9);
    }
}

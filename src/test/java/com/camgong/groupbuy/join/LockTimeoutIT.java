package com.camgong.groupbuy.join;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 다른 트랜잭션이 공구 행 락을 오래 쥐고 있으면 참여 요청은 락 대기 시간(여기서는 1초) 뒤 재시도 없이 바로 BUSY 로 돌아온다.
 * 재시도했다면 재시도 카운터가 올라간다.
 */
@TestPropertySource(properties = "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=1")
class LockTimeoutIT extends JoinTestBase {

    @Autowired
    PlatformTransactionManager txManager;

    @Test
    void aLockWaitTimeoutBecomesBusyWithoutRetrying() throws Exception {
        long id = openGroupBuy();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
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

        long retriesBefore = BenchCounters.RETRIES.sum();
        long start = System.nanoTime();
        joinAs(2, id, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("BUSY"));
        long millis = (System.nanoTime() - start) / 1_000_000;

        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        // 재시도 여부는 시간이 아니라 재시도 카운터로 본다(느린 CI 에서도 흔들리지 않는다). 락 대기를 실제로 겪었는지는 하한만 본다.
        assertThat(BenchCounters.RETRIES.sum()).as("락 대기 시간 초과는 재시도하지 않는다").isEqualTo(retriesBefore);
        assertThat(millis).as("세션 락 대기 1초를 실제로 기다렸다").isGreaterThanOrEqualTo(900L);
        assertThat(secured(id)).isEqualTo(9);
        assertThat(participationRows(id)).isEqualTo(1);
        joinAs(2, id, 1).andExpect(status().isCreated());                         // 락이 풀리면 정상 처리
    }
}

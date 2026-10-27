package com.camgong.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** quickstart 시나리오 18, 19: 알림은 요청 응답을 기다리지 않고, 수신자가 많아도 모두 만들어진다. 실제 비동기 실행기를 켠다. */
@TestPropertySource(properties = "camgong.notification.async.enabled=true")
class NotificationAsyncIT extends NotificationTestBase {

    @MockitoSpyBean
    NotificationWriter writer;

    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void releaseWorkers() {
        release.countDown();
    }

    private void waitForNotifications(int expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (notificationCount() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(notificationCount()).isEqualTo(expected);
    }

    @Test
    void theRequestDoesNotWaitForNotificationCreation() throws Exception {
        doAnswer(inv -> {
            release.await(20, TimeUnit.SECONDS);                    // 알림 쓰기를 붙잡아 둔다
            return inv.callRealMethod();
        }).when(writer).write(any());
        long id = openWithParticipants();

        long start = System.nanoTime();
        cancelAs(ORGANIZER, id).andExpect(status().isNoContent());           // 알림 쓰기가 막혀 있어도 응답이 나간다
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(millis).as("요청 응답 시간").isLessThan(1_000);
        assertThat(notificationCount()).as("아직 알림 없음").isZero();
        release.countDown();
        waitForNotifications(2, 10_000);
        assertThat(recipientsOf("GROUPBUY_CANCELLED")).containsExactly(PARTICIPANT, OUTSIDER);
    }

    @Test
    void aThousandRecipientsAreAllNotifiedAfterwards() throws Exception {
        seedMembers(100, 1099);
        long id = openGroupBuy();
        // 개설자 9 + 참여자 1,000명이 들어가도록 총 수량을 크게 키운다(SQL 시드)
        jdbc.update("UPDATE group_buy SET total_quantity = 2000, secured_quantity = 1009 WHERE id = ?", id);
        for (long m = 100; m < 1100; m++) {
            participate(id, m, 1);
        }

        cancelAs(ORGANIZER, id).andExpect(status().isNoContent());

        waitForNotifications(1000, 60_000);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT recipient_id) FROM notification", Integer.class)).isEqualTo(1000);
    }
}

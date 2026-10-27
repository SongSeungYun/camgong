package com.camgong.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 큐가 가득 차면 알림을 유실로 취급하고(WARN) 원 요청은 영향을 받지 않는다 — CallerRuns 로 요청을 느리게 하지 않는다. */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = { "camgong.notification.async.enabled=true", "camgong.notification.async.core-size=1",
        "camgong.notification.async.max-size=1", "camgong.notification.async.queue-capacity=1" })
class NotificationQueueFullIT extends NotificationTestBase {

    @MockitoSpyBean
    NotificationWriter writer;

    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void releaseWorkers() {
        release.countDown();
    }

    @Test
    void aFullQueueDropsTheNotificationWithAWarningAndTheRequestStillSucceedsQuickly(CapturedOutput output) throws Exception {
        doAnswer(inv -> {
            release.await(20, TimeUnit.SECONDS);
            return inv.callRealMethod();
        }).when(writer).write(any());
        long first = openWithParticipants();
        cancelAs(ORGANIZER, first).andExpect(status().isNoContent());          // 작업자 1명이 붙잡힘
        long second = openGroupBuy(com.camgong.support.GroupBuyBodies.with(com.camgong.support.GroupBuyBodies.valid(), "title", "둘째"));
        seedParticipant(second, PARTICIPANT, 3);
        cancelAs(ORGANIZER, second).andExpect(status().isNoContent());         // 큐 1칸이 참
        long third = openGroupBuy(com.camgong.support.GroupBuyBodies.with(com.camgong.support.GroupBuyBodies.valid(), "title", "셋째"));
        seedParticipant(third, PARTICIPANT, 3);

        long start = System.nanoTime();
        cancelAs(ORGANIZER, third).andExpect(status().isNoContent());          // 큐가 가득: 거부되지만 요청은 성공
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(millis).isLessThan(1_000);
        assertThat(output.getAll()).contains("notification-dropped");
        release.countDown();
        long deadline = System.currentTimeMillis() + 10_000;
        while (notificationCount() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        Thread.sleep(300);
        assertThat(notificationCount()).as("첫째 공구(참여자 2명)와 둘째 공구(참여자 1명)의 알림만, 셋째는 유실").isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE group_buy_id = ?", Integer.class, third)).isZero();
    }
}

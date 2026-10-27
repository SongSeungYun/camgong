package com.camgong.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.camgong.support.Concurrent;

/** quickstart 시나리오 20: 보관 기간(30일)이 지난 알림만 지우고, 반복·동시 실행에도 안전하다. */
class NotificationCleanupIT extends NotificationTestBase {

    @Autowired
    NotificationCleanup cleanup;

    @Test
    void onlyNotificationsOlderThanTheRetentionAreDeleted() {
        insertNotification(PARTICIPANT, "GROUPBUY_COMPLETED", START, false);                                    // 31일 뒤 기준 31일 전
        insertNotification(PARTICIPANT, "GROUPBUY_COMPLETED", START.plus(Duration.ofDays(5)), true);               // 26일 전
        insertNotification(PARTICIPANT, "GROUPBUY_COMPLETED", START.plus(Duration.ofDays(1)).minusSeconds(1), false);  // 30일 + 1초 전
        long fresh = insertNotification(OUTSIDER, "GROUPBUY_FAILED", START.plus(Duration.ofDays(30)), false);
        clock.set(START.plus(Duration.ofDays(31)));

        int deleted = cleanup.run();

        assertThat(deleted).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT id FROM notification", Long.class)).hasSize(2).contains(fresh);
        assertThat(cleanup.run()).as("반복 실행은 안전하다").isZero();
    }

    @Test
    void theBoundaryIsExactlyThirtyDays() {
        long exactly = insertNotification(PARTICIPANT, "GROUPBUY_COMPLETED", START, false);
        clock.set(START.plus(Duration.ofDays(30)));
        assertThat(cleanup.run()).as("정확히 30일 된 알림은 남는다(created_at < now − 30일)").isZero();
        clock.set(START.plus(Duration.ofDays(30)).plusSeconds(1));
        assertThat(cleanup.run()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id FROM notification", Long.class)).doesNotContain(exactly);
    }

    @Test
    void moreThanOneBatchIsDeletedAndTwoCleanupsAtOnceDoNotFail() throws Exception {
        for (int i = 0; i < 2300; i++) {
            jdbc.update("INSERT INTO notification (event_id, recipient_id, type, group_buy_id, group_buy_title, created_at) "
                    + "VALUES (UUID(), ?, 'GROUPBUY_COMPLETED', 1, 't', ?)", PARTICIPANT, java.time.LocalDateTime.of(2026, 9, 1, 0, 0));
        }
        clock.set(START.plus(Duration.ofDays(31)));

        List<Callable<Integer>> calls = new ArrayList<>();
        calls.add(cleanup::run);
        calls.add(cleanup::run);
        List<Integer> deleted = Concurrent.all(calls);

        assertThat(deleted.stream().mapToInt(Integer::intValue).sum()).isEqualTo(2300);
        assertThat(notificationCount()).isZero();
    }
}

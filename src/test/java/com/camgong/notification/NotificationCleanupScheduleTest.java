package com.camgong.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/** 첫 정리는 기동 1분 뒤다: 첫 실행을 주기(1일)만큼 미루면 하루 안에 재시작이 반복되는 환경에서 보관 기간 정리가 한 번도 안 돈다. */
class NotificationCleanupScheduleTest {

    @Test
    void theFirstCleanupRunsAMinuteAfterStartupNotAfterAFullInterval() throws Exception {
        var scheduled = NotificationCleanup.Scheduling.class.getDeclaredMethod("scheduled").getAnnotation(Scheduled.class);
        assertThat(scheduled.initialDelayString()).isEqualTo("PT1M");
    }
}

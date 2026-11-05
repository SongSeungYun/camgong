package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import com.camgong.support.IntegrationTestBase;

/** camgong.auth.cleanup.enabled=false 면 다른 기능이 스케줄링을 켜도(여기서는 005 마감 확정) 세션 정리는 예약되지 않는다. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = { "camgong.scheduler.close.enabled=true", "camgong.scheduler.close.interval=1h" })
class SessionCleanupWiringIT extends IntegrationTestBase {

    @Autowired
    ScheduledTaskHolder tasks;

    @Test
    void disabledSessionCleanupIsNotScheduledEvenWhenSchedulingIsOnForSomethingElse() {
        assertThat(tasks.getScheduledTasks()).isNotEmpty();      // 스케줄링 자체는 켜져 있다
        assertThat(tasks.getScheduledTasks()).extracting(Object::toString)        // "패키지.클래스.메서드"
                .contains("com.camgong.groupbuy.close.CloseExpiredScheduler.run")
                .noneMatch(t -> t.contains("SessionCleanup"));
    }
}

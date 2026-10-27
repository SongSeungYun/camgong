package com.camgong.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 청크 크기 1 이면 청크 안 연속 포기를 셀 수 없어 중단 규칙이 무력해진다: 최소 2 로 기동에서 거부한다. */
class NotificationPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(NotificationProperties.Binding.class))
            .withPropertyValues("camgong.notification.async.enabled=true", "camgong.notification.async.core-size=1",
                    "camgong.notification.async.max-size=1", "camgong.notification.async.queue-capacity=1",
                    "camgong.notification.insert-chunk-size=500", "camgong.notification.retry-attempts=3",
                    "camgong.notification.retry-base-delay=1s", "camgong.notification.retention=30d",
                    "camgong.notification.cleanup.enabled=true", "camgong.notification.cleanup.interval=1d");

    @Test
    void validValuesBind() {
        runner.run(ctx -> assertThat(ctx.getBean(NotificationProperties.class).insertChunkSize()).isEqualTo(500));
    }

    @Test
    void aChunkSizeBelowTwoIsRejected() {
        runner.withPropertyValues("camgong.notification.insert-chunk-size=1").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("camgong.notification.insert-chunk-size=2").run(ctx -> assertThat(ctx).hasNotFailed());
    }
}

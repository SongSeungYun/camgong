package com.camgong.notification;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/** 알림 설정(contracts/notification-api.md 설정 표). 잘못된 값은 기동에서 거부한다. */
@Validated
@ConfigurationProperties("camgong.notification")
public record NotificationProperties(@Valid @NotNull Async async, @Min(2) int insertChunkSize, @Min(0) int retryAttempts,
        @NotNull Duration retryBaseDelay, @NotNull Duration retention, @Valid @NotNull Cleanup cleanup) {

    public NotificationProperties {
        if (retryBaseDelay != null && retryBaseDelay.isNegative()) {   // 음수면 재시도 대기(Thread.sleep)가 예외로 끝나 남은 알림이 유실된다
            throw new IllegalArgumentException("camgong.notification.retry-base-delay 는 음수일 수 없다: " + retryBaseDelay);
        }
        if (retention != null && !retention.isPositive()) {             // 0 이하면 정리가 방금 만든 알림까지 지운다
            throw new IllegalArgumentException("camgong.notification.retention 은 0보다 커야 한다: " + retention);
        }
    }

    /** enabled=false 면 알림을 요청 스레드에서 바로 만든다(테스트용). */
    public record Async(boolean enabled, @Min(1) int coreSize, @Min(1) int maxSize, @Min(1) int queueCapacity) {
    }

    public record Cleanup(boolean enabled, @NotNull Duration interval) {

        public Cleanup {
            if (interval != null && !interval.isPositive()) {
                throw new IllegalArgumentException("camgong.notification.cleanup.interval 은 0보다 커야 한다: " + interval);
            }
        }
    }

    @Configuration
    @EnableConfigurationProperties(NotificationProperties.class)
    static class Binding {
    }
}

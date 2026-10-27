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

    /** enabled=false 면 알림을 요청 스레드에서 바로 만든다(테스트용). */
    public record Async(boolean enabled, @Min(1) int coreSize, @Min(1) int maxSize, @Min(1) int queueCapacity) {
    }

    public record Cleanup(boolean enabled, @NotNull Duration interval) {
    }

    @Configuration
    @EnableConfigurationProperties(NotificationProperties.class)
    static class Binding {
    }
}

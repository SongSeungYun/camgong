package com.camgong.groupbuy.close;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 마감 확정 스케줄러 설정(contracts/events-and-config.md). 잘못된 값은 기동 때 거부한다: batch-size 가 0 이하면 매 실행이 예외로 끝나고,
 * max-rounds 가 0 이면 아무 일도 하지 않으면서 조용히 지나가기 때문이다.
 */
@Validated
@ConfigurationProperties("camgong.scheduler.close")
public record CloseProperties(boolean enabled, @NotNull Duration interval, @NotNull Duration margin,
        @Min(1) int batchSize, @Min(1) int maxRounds) {

    public CloseProperties {
        if (interval != null && (interval.isZero() || interval.isNegative())) {
            throw new IllegalArgumentException("camgong.scheduler.close.interval 은 0보다 커야 한다: " + interval);
        }
        if (margin != null && margin.isNegative()) {
            throw new IllegalArgumentException("camgong.scheduler.close.margin 은 음수일 수 없다: " + margin);
        }
    }

    @Configuration
    @EnableConfigurationProperties(CloseProperties.class)
    static class Binding {
    }
}

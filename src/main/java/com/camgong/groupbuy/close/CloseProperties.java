package com.camgong.groupbuy.close;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 마감 확정 스케줄러 설정(contracts/events-and-config.md). */
@ConfigurationProperties("camgong.scheduler.close")
public record CloseProperties(boolean enabled, Duration interval, Duration margin, int batchSize, int maxRounds) {

    @Configuration
    @EnableConfigurationProperties(CloseProperties.class)
    static class Binding {
    }
}

package com.camgong.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 만료된 세션·풀린 잠금 기록·쓸모없어진 가입 신청을 지운다. */
// ponytail: 단일 인스턴스 전제, 다중 인스턴스면 락 필요
@Component
class SessionCleanup {

    /** 자동 실행 스위치: 테스트는 꺼 두고 run() 을 직접 부른다. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "camgong.auth.cleanup.enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduling {
    }

    private static final Logger log = LoggerFactory.getLogger(SessionCleanup.class);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final AuthProperties props;

    SessionCleanup(JdbcClient jdbc, Clock clock, AuthProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    void run() {
        Instant now = Instant.now(clock);
        int sessions = jdbc.sql("DELETE FROM login_session WHERE last_used_at < :cut")
                .param("cut", at(now.minus(props.sessionTtl()))).update();
        int attempts = jdbc.sql("DELETE FROM login_attempt WHERE updated_at < :cut "
                + "AND (locked_until IS NULL OR locked_until <= :now)")
                .param("cut", at(now.minus(Duration.ofDays(1)))).param("now", at(now)).update();
        int pending = jdbc.sql("DELETE FROM pending_signup WHERE token_expires_at < :expiredCut "
                + "OR used_at < :usedCut")
                .param("expiredCut", at(now.minus(Duration.ofDays(7)))).param("usedCut", at(now.minus(Duration.ofDays(1))))
                .update();
        log.info("cleanup: sessions={} attempts={} pending={}", sessions, attempts, pending);
    }

    private static LocalDateTime at(Instant t) {
        return LocalDateTime.ofInstant(t, ZoneOffset.UTC);
    }
}

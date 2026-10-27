package com.camgong.notification;

import java.time.Clock;
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

/**
 * 보관 기간(retention)이 지난 알림을 1,000건씩 지운다. 영향 행이 0 이 될 때까지 반복하며, 같은 정리가 동시에 돌아도 안전하다
 * (이미 지워진 행은 다시 지워지지 않고 오류도 없다). 스케줄러는 005 와 같은 패턴(fixedDelay, 설정으로 끄기)이고, 테스트는 run() 을 직접 부른다.
 */
@Component
public class NotificationCleanup {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanup.class);
    private static final int BATCH = 1000;

    private final JdbcClient jdbc;
    private final Clock clock;
    private final NotificationProperties props;

    NotificationCleanup(JdbcClient jdbc, Clock clock, NotificationProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.props = props;
    }

    /** @return 지운 건수 */
    public int run() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(Instant.now(clock).minus(props.retention()), ZoneOffset.UTC);
        int total = 0;
        int deleted;
        do {
            deleted = jdbc.sql("DELETE FROM notification WHERE created_at < :cutoff LIMIT " + BATCH).param("cutoff", cutoff).update();
            total += deleted;
        } while (deleted > 0);
        if (total > 0) {
            log.info("notification-cleanup deleted={}", total);
        }
        return total;
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "camgong.notification.cleanup.enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduling {

        private final NotificationCleanup cleanup;

        Scheduling(NotificationCleanup cleanup) {
            this.cleanup = cleanup;
        }

        @Scheduled(fixedDelayString = "${camgong.notification.cleanup.interval:1d}", initialDelayString = "${camgong.notification.cleanup.interval:1d}")
        void scheduled() {
            try {
                cleanup.run();
            } catch (RuntimeException e) {
                log.warn("notification-cleanup-failed reason={}", e.toString());
            }
        }
    }
}

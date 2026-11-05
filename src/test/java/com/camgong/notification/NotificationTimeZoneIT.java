package com.camgong.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** created_at 은 JVM 시간대·접속 시간대가 달라도 UTC 벽시계 그대로 저장된다(목록 시각·30일 정리의 기준). */
class NotificationTimeZoneIT extends NotificationTestBase {

    @Autowired
    NotificationProperties props;

    @Value("${spring.datasource.url}")
    String url;

    @Value("${spring.datasource.username}")
    String username;

    @Value("${spring.datasource.password}")
    String password;

    @Test
    void createdAtIsStoredAsUtcWallClockEvenWhenTheJvmAndConnectionTimeZonesDiffer() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        try (SingleConnectionDataSource ds = new SingleConnectionDataSource(url + "&connectionTimeZone=UTC", username, password, true)) {
            NotificationWriter writer = new NotificationWriter(new JdbcTemplate(ds), new DataSourceTransactionManager(ds), props);
            writer.write(List.of(new NotificationRow(UUID.randomUUID().toString(), 1L, NotificationType.GROUPBUY_COMPLETED, 1L, "t", null, START)));
        } finally {
            TimeZone.setDefault(original);
        }

        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') FROM notification", String.class))
                .isEqualTo("2026-10-27 00:00:00");
    }
}

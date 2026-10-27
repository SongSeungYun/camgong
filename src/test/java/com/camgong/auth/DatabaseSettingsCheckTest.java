package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.camgong.support.IntegrationTestBase;

/** useAffectedRows=true 가 빠진 연결은 기동 때 거부된다(한도·잠금·토큰 단일 사용이 이 판정에 기댄다). */
class DatabaseSettingsCheckTest extends IntegrationTestBase {

    @Autowired
    DataSource dataSource;

    @Test
    void passesWithTheConfiguredUrl() {
        assertThatCode(() -> new DatabaseSettingsCheck(dataSource).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void failsWhenUseAffectedRowsIsMissingFromTheUrl() throws Exception {
        String url = dataSource.getConnection().getMetaData().getURL().replace("useAffectedRows=true", "useAffectedRows=false");
        // Testcontainers MySQL 의 기본 계정(test/test)
        DriverManagerDataSource without = new DriverManagerDataSource(url, "test", "test");

        assertThatThrownBy(() -> new DatabaseSettingsCheck(without).afterPropertiesSet())
                .hasMessageContaining("useAffectedRows");
    }

    @Test
    void refusesToStartOnTheSchemaFromBeforeChangeB2() {
        jdbc.execute("ALTER TABLE pending_signup ADD COLUMN display_name VARCHAR(20) NULL");
        try {
            assertThatThrownBy(() -> new DatabaseSettingsCheck(dataSource).afterPropertiesSet())
                    .hasMessageContaining("새로 만들어야");
        } finally {
            jdbc.execute("ALTER TABLE pending_signup DROP COLUMN display_name");
        }
        assertThatCode(() -> new DatabaseSettingsCheck(dataSource).afterPropertiesSet()).doesNotThrowAnyException();
    }
}

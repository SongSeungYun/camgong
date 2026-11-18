package com.camgong.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SchemaIT extends IntegrationTestBase {

    @Test
    void contextLoadsAndSchemaValidatesWithSeededMembers() {
        // ddl-auto=validate 를 통과해 컨텍스트가 떴고, 시드 회원 3명이 있다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member", Integer.class)).isEqualTo(3);
    }

    @Test
    void schemaComesFromFlywayMigrationsNotFromSqlInit() {
        // 빈 DB 에 V1 이 적용되었고(그 뒤 validate 통과), 기동 때마다 다시 도는 schema.sql 은 더 없다
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class))
                .containsExactly("1");
        assertThat(new ClassPathResource("schema.sql").exists()).isFalse();
    }
}

package com.camgong.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class SchemaIT extends IntegrationTestBase {

    @Autowired
    DataSource dataSource;

    @Test
    void contextLoadsAndSchemaValidatesWithSeededMembers() {
        // ddl-auto=validate 를 통과해 컨텍스트가 떴고, 시드 회원 3명이 있다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member", Integer.class)).isEqualTo(3);
    }

    @Test
    void schemaSqlIsSafeToRunAgain() {
        // 앱 재시작은 schema.sql 을 다시 실행한다
        assertThatCode(() -> new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource))
                .doesNotThrowAnyException();
    }
}

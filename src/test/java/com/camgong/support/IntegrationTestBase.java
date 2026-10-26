package com.camgong.support;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/** 모든 통합 테스트의 기반. 실제 MySQL 8.4 컨테이너(헌법 IV)와 조작 가능한 시계를 쓴다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestBase.TestClockConfig.class)
public abstract class IntegrationTestBase {

    public static final Instant START = Instant.parse("2026-10-27T00:00:00Z");

    public static final long ORGANIZER = 1L;
    public static final long PARTICIPANT = 2L;
    public static final long OUTSIDER = 3L;

    // 클래스마다 컨테이너를 다시 띄우지 않도록 JVM당 한 번만 시작한다. 종료는 Ryuk가 맡는다.
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        // useAffectedRows=true: CreateGuard 영향 행 0/1/2 판정에 필요(docs/spikes.md #1)
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?useAffectedRows=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetState() {
        clock.set(START);
        jdbc.update("DELETE FROM participation");
        jdbc.update("DELETE FROM group_buy");
        jdbc.update("DELETE FROM create_guard");
        jdbc.update("DELETE FROM member");
        jdbc.update("INSERT INTO member (id, name) VALUES (1, '개설자'), (2, '참여자'), (3, '외부인')");
    }

    /**
     * 요청자 인증은 이 헬퍼 한 곳으로만 한다. 지금은 X-Member-Id 헤더이고,
     * 002에서 세션 쿠키로 바꿀 때 이 메서드만 고친다. 테스트 본문에 헤더 이름을 쓰지 않는다.
     */
    protected static RequestPostProcessor as(long memberId) {
        return request -> {
            request.addHeader("X-Member-Id", String.valueOf(memberId));
            return request;
        };
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }
    }
}

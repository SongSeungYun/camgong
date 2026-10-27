package com.camgong.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.mysql.MySQLContainer;

import com.camgong.auth.LoggingMailSender;
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
        registry.add("camgong.auth.bcrypt-cost", () -> 4); // 테스트 속도
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected EventCollector events;

    @Autowired
    protected LoggingMailSender mail;

    @BeforeEach
    void resetState() {
        clock.set(START);
        events.clear();
        mail.clear();
        jdbc.update("DELETE FROM login_session");
        jdbc.update("DELETE FROM pending_signup");
        jdbc.update("DELETE FROM login_attempt");
        jdbc.update("DELETE FROM participation");
        jdbc.update("DELETE FROM group_buy");
        jdbc.update("DELETE FROM create_guard");
        jdbc.update("DELETE FROM member");
        seedMember(1, "개설자");
        seedMember(2, "참여자");
        seedMember(3, "외부인");
    }

    /** 시드 회원: 이메일 m{id}@school.ac.kr, 비밀번호 해시는 로그인할 수 없는 더미. */
    protected void seedMember(long id, String name) {
        jdbc.update("INSERT INTO member (id, name, display_name_key, email, email_key, password_hash, "
                + "consent_at, consent_version, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, name, name.toLowerCase(), "m" + id + "@school.ac.kr", "m" + id + "@school.ac.kr",
                "$2a$04$dummydummydummydummydummydummydummydummydummydummydummy", LocalDateTime.ofInstant(START, ZoneOffset.UTC),
                "v1", LocalDateTime.ofInstant(START, ZoneOffset.UTC));
    }

    /** 개설자(1)가 기본 본문으로 공구를 열고 id 를 돌려준다. 같은 본문은 10초 가드에 걸리므로 테스트당 한 번만 쓴다. */
    protected long openGroupBuy() throws Exception {
        return openGroupBuy(GroupBuyBodies.valid());
    }

    protected long openGroupBuy(Map<String, Object> body) throws Exception {
        String location = mockMvc.perform(post("/api/groupbuys").with(as(ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content(GroupBuyBodies.json(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    /** 참여 기능이 없으므로 참여 행과 확보 수량을 SQL 로 함께 맞춘다. */
    protected void seedParticipant(long groupBuyId, long memberId, int quantity) {
        jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, ?, ?, ?)",
                groupBuyId, memberId, quantity, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        jdbc.update("UPDATE group_buy SET secured_quantity = secured_quantity + ? WHERE id = ?", quantity, groupBuyId);
    }

    protected ResultActions updateAs(long memberId, long groupBuyId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/api/groupbuys/" + groupBuyId).with(as(memberId))
                .contentType(MediaType.APPLICATION_JSON).content(GroupBuyBodies.json(body)));
    }

    protected ResultActions cancelAs(long memberId, long groupBuyId) throws Exception {
        return mockMvc.perform(post("/api/groupbuys/" + groupBuyId + "/cancel").with(as(memberId)));
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

        @Bean
        EventCollector eventCollector() {
            return new EventCollector();
        }
    }
}

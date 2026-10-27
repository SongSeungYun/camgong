package com.camgong.auth;

import static com.camgong.support.AuthBodies.signup;
import static com.camgong.support.AuthBodies.verify;
import static com.camgong.support.GroupBuyBodies.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 2, 6, 14(일부) */
class VerifyEmailIT extends IntegrationTestBase {

    private void requestSignup(String email, String name) throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(signup(email, name, "password1")))).andExpect(status().isAccepted());
    }

    private String lastToken() {
        String link = mail.sent().get(mail.sent().size() - 1).verifyLink();
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private ResultActions verifyToken(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @Test
    void verifyCreatesTheMemberAndLogsThemIn() throws Exception {
        requestSignup("new@school.ac.kr", "홍길동");

        MvcResult result = verifyToken(verify(lastToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.displayName").value("홍길동"))
                .andExpect(jsonPath("$.member.id").isNumber())
                .andExpect(jsonPath("$.member.email").doesNotExist())
                .andReturn();

        String cookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).contains("camgong_session=").contains("HttpOnly").contains("SameSite=Lax");
        Map<String, Object> m = jdbc.queryForMap("SELECT * FROM member WHERE email_key = 'new@school.ac.kr'");
        assertThat(m.get("display_name_key")).isEqualTo("홍길동");
        assertThat(m.get("consent_version")).isEqualTo("v1");
        assertThat(m.get("consent_at")).isNotNull();
        assertThat((String) m.get("password_hash")).startsWith("$2");
        assertThat(count("login_session")).isEqualTo(1);
        assertThat(count("member")).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT used_at IS NOT NULL FROM pending_signup", Boolean.class)).isTrue();
    }

    @Test
    void sameTokenTwiceIsTokenUsed() throws Exception {
        requestSignup("new@school.ac.kr", "홍길동");
        String token = lastToken();
        verifyToken(verify(token)).andExpect(status().isOk());

        verifyToken(verify(token)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("TOKEN_USED"));
        assertThat(count("member")).isEqualTo(4);
    }

    @Test
    void takenDisplayNameKeepsTheTokenValidUntilANewNameIsGiven() throws Exception {
        requestSignup("new@school.ac.kr", " 개설자 ");           // 시드 회원 "개설자"와 같은 이름
        String token = lastToken();

        verifyToken(verify(token)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("DISPLAY_NAME_TAKEN"));
        assertThat(count("member")).isEqualTo(3);
        assertThat(count("login_session")).isZero();
        assertThat(jdbc.queryForObject("SELECT used_at IS NULL FROM pending_signup", Boolean.class)).isTrue();

        verifyToken(verify(token, "새이름")).andExpect(status().isOk())
                .andExpect(jsonPath("$.member.displayName").value("새이름"));
        assertThat(count("member")).isEqualTo(4);
    }

    @Test
    void nameIsNotReservedBeforeVerificationAndFirstToVerifyWins() throws Exception {
        requestSignup("a@school.ac.kr", "같은이름");
        String tokenA = lastToken();
        requestSignup("b@school.ac.kr", "같은이름");
        String tokenB = lastToken();

        verifyToken(verify(tokenB)).andExpect(status().isOk());
        verifyToken(verify(tokenA)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("DISPLAY_NAME_TAKEN"));
    }

    @Test
    void expiredAndUnknownTokens() throws Exception {
        requestSignup("new@school.ac.kr", "홍길동");
        String token = lastToken();

        verifyToken(verify("no-such-token")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_TOKEN"));

        clock.advance(Duration.ofHours(24).minusSeconds(1));
        // 만료 직전에는 아직 유효하다는 것까지 확인하면 토큰이 소모되므로 아래에서 다른 신청으로 본다.
        requestSignup("other@school.ac.kr", "다른이름");
        String fresh = lastToken();
        clock.advance(Duration.ofHours(24));                      // fresh 는 발급 후 정확히 24시간

        verifyToken(verify(token)).andExpect(status().isGone())
                .andExpect(jsonPath("$.title").value("TOKEN_EXPIRED"));
        verifyToken(verify(fresh)).andExpect(status().isGone())
                .andExpect(jsonPath("$.title").value("TOKEN_EXPIRED"));
        assertThat(count("member")).isEqualTo(3);
    }

    @Test
    void concurrentVerifyOfTheSameTokenCreatesExactlyOneMember() throws Exception {
        requestSignup("new@school.ac.kr", "홍길동");
        String token = lastToken();

        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            // 이메일 UNIQUE 가 두 번째를 막아도 409 가 나오므로, 이유가 TOKEN_USED 인지까지 본다.
            Callable<String> call = () -> {
                var res = verifyToken(verify(token)).andReturn().getResponse();
                return res.getStatus() + (res.getStatus() == 200 ? "" : ":" + JsonPath.read(res.getContentAsString(), "$.title"));
            };
            results.add(pool.submit(call));
        }
        List<String> statuses = new ArrayList<>();
        for (Future<String> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(s -> s.equals("200")).hasSize(1);
        assertThat(statuses).filteredOn(s -> !s.equals("200")).allMatch(s -> s.equals("409:TOKEN_USED"));
        assertThat(count("member")).isEqualTo(4);
        assertThat(count("login_session")).isEqualTo(1);
    }

    @Test
    void tokenIsStillValidJustBeforeExpiry() throws Exception {
        requestSignup("new@school.ac.kr", "홍길동");
        String token = lastToken();

        clock.advance(Duration.ofHours(24).minusSeconds(1));

        verifyToken(verify(token)).andExpect(status().isOk());
    }

    @Test
    void takenNameIsDetectedIgnoringCase() throws Exception {
        jdbc.update("UPDATE member SET name = 'Kim', display_name_key = 'kim' WHERE id = 1");
        requestSignup("new@school.ac.kr", "KIM");

        verifyToken(verify(lastToken())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("DISPLAY_NAME_TAKEN"));
    }

    /** MySQL 오류 메시지에는 사용자가 쓴 값이 들어간다. 값에 키 이름이 있어도 이유를 잘못 가르면 안 된다. */
    @Test
    void emailCollisionIsNotMistakenForNameCollisionWhenTheValueContainsAKeyName() throws Exception {
        requestSignup("display_name_key@school.ac.kr", "홍길동");
        String token = lastToken();
        jdbc.update("INSERT INTO member (name, display_name_key, email, email_key, password_hash, consent_at, "
                + "consent_version, created_at) VALUES ('선점자', '선점자', 'display_name_key@school.ac.kr', "
                + "'display_name_key@school.ac.kr', 'x', NOW(), 'v1', NOW())");

        verifyToken(verify(token)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("ALREADY_REGISTERED"));
    }
}

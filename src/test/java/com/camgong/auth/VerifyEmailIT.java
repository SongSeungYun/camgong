package com.camgong.auth;

import static com.camgong.support.AuthBodies.login;
import static com.camgong.support.AuthBodies.signup;
import static com.camgong.support.AuthBodies.verify;
import static com.camgong.support.GroupBuyBodies.json;
import static com.camgong.support.GroupBuyBodies.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.Concurrent;
import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 2, 5(인증 쪽), 6, 14(일부) — 변경 B2: 이름·비밀번호·동의는 인증에서 정한다 */
class VerifyEmailIT extends IntegrationTestBase {

    private void requestSignup(String email) throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(signup(email)))).andExpect(status().isAccepted());
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

    private boolean tokenUnused() {
        return jdbc.queryForObject("SELECT used_at IS NULL FROM pending_signup", Boolean.class);
    }

    @Test
    void verifyCreatesTheMemberWithTheChosenNameAndPasswordAndLogsThemIn() throws Exception {
        requestSignup("new@school.ac.kr");

        MvcResult result = verifyToken(verify(lastToken(), "홍길동", "my-chosen-pw1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.displayName").value("홍길동"))
                .andExpect(jsonPath("$.member.id").isNumber())
                .andExpect(jsonPath("$.member.email").doesNotExist())
                .andReturn();

        String cookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).contains("camgong_session=").contains("HttpOnly").contains("SameSite=Lax");
        Map<String, Object> m = jdbc.queryForMap("SELECT * FROM member WHERE email_key = 'new@school.ac.kr'");
        assertThat(m.get("name")).isEqualTo("홍길동");
        assertThat(m.get("display_name_key")).isEqualTo("홍길동");
        assertThat(m.get("consent_version")).isEqualTo("v1");
        assertThat(m.get("consent_at")).isNotNull();
        assertThat((String) m.get("password_hash")).startsWith("$2").doesNotContain("my-chosen-pw1");
        assertThat(count("login_session")).isEqualTo(1);
        assertThat(count("member")).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT used_at IS NOT NULL FROM pending_signup", Boolean.class)).isTrue();

        // 인증 때 정한 비밀번호로 로그인된다
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(login("new@school.ac.kr", "my-chosen-pw1")))).andExpect(status().isOk());
    }

    @Test
    void nameAndPasswordComeOnlyFromTheVerifyRequestNotFromWhoAppliedFirst() throws Exception {
        // 남이 먼저 이 이메일로 신청해 뒀어도(신청에는 이름·비밀번호가 없다) 링크를 누른 사람이 정한 값으로 가입된다
        requestSignup("victim@school.ac.kr");
        verifyToken(verify(lastToken(), "피해자", "victims-own-pw1")).andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(login("victim@school.ac.kr", "victims-own-pw1")))).andExpect(status().isOk());
    }

    @Test
    void sameTokenTwiceIsTokenUsed() throws Exception {
        requestSignup("new@school.ac.kr");
        String token = lastToken();
        verifyToken(verify(token)).andExpect(status().isOk());

        verifyToken(verify(token)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("TOKEN_USED"));
        assertThat(count("member")).isEqualTo(4);
    }

    @Test
    void takenDisplayNameKeepsTheTokenValidUntilANewNameIsGiven() throws Exception {
        requestSignup("new@school.ac.kr");
        String token = lastToken();

        verifyToken(verify(token, " 개설자 ")).andExpect(status().isConflict())      // 시드 회원 "개설자"와 같은 이름
                .andExpect(jsonPath("$.title").value("DISPLAY_NAME_TAKEN"));
        assertThat(count("member")).isEqualTo(3);
        assertThat(count("login_session")).isZero();
        assertThat(tokenUnused()).isTrue();

        verifyToken(verify(token, "새이름")).andExpect(status().isOk())
                .andExpect(jsonPath("$.member.displayName").value("새이름"));
        assertThat(count("member")).isEqualTo(4);
    }

    @Test
    void twoApplicantsChoosingTheSameNameAtVerifyTimeFirstToFinishWins() throws Exception {
        requestSignup("a@school.ac.kr");
        String tokenA = lastToken();
        requestSignup("b@school.ac.kr");
        String tokenB = lastToken();

        verifyToken(verify(tokenB, "같은이름")).andExpect(status().isOk());
        verifyToken(verify(tokenA, "같은이름")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("DISPLAY_NAME_TAKEN"));
        assertThat(count("member")).isEqualTo(4);
    }

    @Test
    void takenNameIsDetectedIgnoringCase() throws Exception {
        jdbc.update("UPDATE member SET name = 'Kim', display_name_key = 'kim' WHERE id = 1");
        requestSignup("new@school.ac.kr");

        verifyToken(verify(lastToken(), "KIM")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("DISPLAY_NAME_TAKEN"));
    }

    @Test
    void expiredAndUnknownTokens() throws Exception {
        requestSignup("new@school.ac.kr");
        String token = lastToken();

        verifyToken(verify("no-such-token")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_TOKEN"));

        clock.advance(Duration.ofHours(24).minusSeconds(1));
        // 만료 직전에는 아직 유효하다는 것까지 확인하면 토큰이 소모되므로 아래에서 다른 신청으로 본다.
        requestSignup("other@school.ac.kr");
        String fresh = lastToken();
        clock.advance(Duration.ofHours(24));                      // fresh 는 발급 후 정확히 24시간

        verifyToken(verify(token)).andExpect(status().isGone())
                .andExpect(jsonPath("$.title").value("TOKEN_EXPIRED"));
        verifyToken(verify(fresh)).andExpect(status().isGone())
                .andExpect(jsonPath("$.title").value("TOKEN_EXPIRED"));
        assertThat(count("member")).isEqualTo(3);
    }

    @Test
    void tokenErrorsComeBeforeBodyErrors() throws Exception {
        requestSignup("new@school.ac.kr");
        String used = lastToken();
        verifyToken(verify(used)).andExpect(status().isOk());

        Map<String, Object> garbage = verify("no-such-token", "x", "short");
        verifyToken(garbage).andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("INVALID_TOKEN"));
        verifyToken(with(garbage, "token", used)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("TOKEN_USED"));
        verifyToken(Map.of()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("INVALID_TOKEN"));
    }

    @Test
    void everyBodyErrorIsReportedByFieldAndLeavesTheTokenUsable() throws Exception {
        requestSignup("new@school.ac.kr");
        String token = lastToken();

        record Case(String field, Map<String, Object> body) {
        }
        List<Case> cases = List.of(
                new Case("displayName", verify(token, "가")),
                new Case("displayName", verify(token, "가".repeat(21))),
                new Case("displayName", verify(token, "   ")),
                new Case("displayName", with(verify(token), "displayName", null)),
                new Case("password", verify(token, "홍길동", "1234567")),
                new Case("password", verify(token, "홍길동", "가".repeat(24) + "a")),         // 73바이트
                new Case("password", verify(token, "홍길동", "a".repeat(73))),
                new Case("password", verify(token, "홍길동", " ".repeat(8))),
                new Case("password", with(verify(token), "password", null)),
                new Case("consent", with(verify(token), "consent", false)),
                new Case("consent", with(verify(token), "consent", null)),
                new Case("consentVersion", with(verify(token), "consentVersion", null)),
                new Case("consentVersion", with(verify(token), "consentVersion", " ")));
        for (Case c : cases) {
            verifyToken(c.body()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field").value(hasItem(c.field())));
            assertThat(tokenUnused()).as(c.field() + " " + c.body()).isTrue();
        }
        assertThat(count("member")).isEqualTo(3);

        Map<String, Object> onlyToken = new LinkedHashMap<>();
        onlyToken.put("token", token);
        verifyToken(onlyToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("displayName")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("consent")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("consentVersion")));
        assertThat(tokenUnused()).isTrue();

        verifyToken(verify(token, "홍길동", "fixed-password1")).andExpect(status().isOk());      // 고쳐서 다시 → 같은 토큰으로 성공
    }

    @Test
    void boundaryValuesAreAccepted() throws Exception {
        requestSignup("a@school.ac.kr");
        verifyToken(verify(lastToken(), "가나", "a".repeat(8))).andExpect(status().isOk());          // 이름 2자, 비밀번호 8자
        requestSignup("b@school.ac.kr");
        verifyToken(verify(lastToken(), "가".repeat(20), "a".repeat(72))).andExpect(status().isOk());  // 이름 20자, 72바이트
        requestSignup("c@school.ac.kr");
        verifyToken(verify(lastToken(), "다라마", "가".repeat(24))).andExpect(status().isOk());      // 한글 72바이트
    }

    @Test
    void aNameWhoseLowercaseFormIsLongerThanTheKeyColumnIsRejectedNotAServerError() throws Exception {
        requestSignup("new@school.ac.kr");
        String token = lastToken();
        // 'İ'(U+0130)는 소문자로 바꾸면 두 글자('i̇')가 되어 20자 이름도 키가 40자가 된다
        verifyToken(verify(token, "İ".repeat(20))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("displayName")));
        assertThat(tokenUnused()).isTrue();
        verifyToken(verify(token, "홍길동")).andExpect(status().isOk());
    }

    @Test
    void emailTakenBetweenApplyingAndVerifyingRollsBackAndKeepsTheTokenValid() throws Exception {
        requestSignup("new@school.ac.kr");
        String token = lastToken();
        jdbc.update("INSERT INTO member (name, display_name_key, email, email_key, password_hash, consent_at, "
                + "consent_version, created_at) VALUES ('선점자', '선점자', 'new@school.ac.kr', 'new@school.ac.kr', "
                + "'x', NOW(), 'v1', NOW())");

        verifyToken(verify(token)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("ALREADY_REGISTERED"));
        assertThat(tokenUnused()).isTrue();
        assertThat(count("login_session")).isZero();
    }

    /** MySQL 오류 메시지에는 사용자가 쓴 값이 들어간다. 값에 키 이름이 있어도 이유를 잘못 가르면 안 된다. */
    @Test
    void emailCollisionIsNotMistakenForNameCollisionWhenTheValueContainsAKeyName() throws Exception {
        requestSignup("display_name_key@school.ac.kr");
        String token = lastToken();
        jdbc.update("INSERT INTO member (name, display_name_key, email, email_key, password_hash, consent_at, "
                + "consent_version, created_at) VALUES ('선점자', '선점자', 'display_name_key@school.ac.kr', "
                + "'display_name_key@school.ac.kr', 'x', NOW(), 'v1', NOW())");

        verifyToken(verify(token)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("ALREADY_REGISTERED"));
    }

    @Test
    void concurrentVerifyOfTheSameTokenCreatesExactlyOneMember() throws Exception {
        requestSignup("new@school.ac.kr");
        String token = lastToken();

        List<Callable<String>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> {
                var res = verifyToken(verify(token)).andReturn().getResponse();
                return res.getStatus() + (res.getStatus() == 200 ? "" : ":" + JsonPath.read(res.getContentAsString(), "$.title"));
            });
        }
        List<String> statuses = Concurrent.all(calls);

        assertThat(statuses).filteredOn(s -> s.equals("200")).hasSize(1);
        assertThat(statuses).filteredOn(s -> !s.equals("200")).allMatch(s -> s.equals("409:TOKEN_USED"));
        assertThat(count("member")).isEqualTo(4);
        assertThat(count("login_session")).isEqualTo(1);
    }

    @Test
    void tokenIsStillValidJustBeforeExpiry() throws Exception {
        requestSignup("new@school.ac.kr");
        String token = lastToken();

        clock.advance(Duration.ofHours(24).minusSeconds(1));

        verifyToken(verify(token)).andExpect(status().isOk());
    }
}

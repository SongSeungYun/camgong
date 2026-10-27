package com.camgong.auth;

import static com.camgong.support.AuthBodies.signup;
import static com.camgong.support.GroupBuyBodies.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 1, 4, 5(신청 쪽) — 변경 B2: 신청은 이메일 하나다 */
class SignupIT extends IntegrationTestBase {

    private ResultActions submit(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private int pendingRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM pending_signup", Integer.class);
    }

    private int members() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM member", Integer.class);
    }

    @Test
    void signupSendsOneMailAndCreatesNoMemberYet() throws Exception {
        submit(signup("new@school.ac.kr"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value("new@school.ac.kr"))
                .andExpect(jsonPath("$.resendAvailableAt").value("2026-10-27T09:01:00+09:00"));

        assertThat(mail.sent()).hasSize(1);
        assertThat(mail.sent().get(0).toEmail()).isEqualTo("new@school.ac.kr");
        assertThat(mail.sent().get(0).verifyLink()).startsWith("http://localhost:5173/verify?token=");
        assertThat(pendingRows()).isEqualTo(1);
        assertThat(members()).isEqualTo(3);                        // 시드 3명뿐, 인증 전에는 회원이 아니다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_session", Integer.class)).isZero();
    }

    @Test
    void storesOnlyEmailTokenHashAndLimitColumns() throws Exception {
        submit(signup("  Mixed.Case@School.AC.KR ")).andExpect(status().isAccepted());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM pending_signup");
        assertThat(row.get("email_key")).isEqualTo("mixed.case@school.ac.kr");
        assertThat(row.get("email")).isEqualTo("Mixed.Case@School.AC.KR");
        String token = mail.sent().get(0).verifyLink().split("token=")[1];
        assertThat(row.get("token_hash")).isEqualTo(Tokens.sha256Hex(token)).isNotEqualTo(token);
        assertThat(row.get("sent_count")).isEqualTo(1);
        assertThat(row.get("used_at")).isNull();
        // 이름·비밀번호·동의는 신청이 아니라 인증 단계에서 받는다: 저장할 컬럼 자체가 없다
        List<String> columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'pending_signup'", String.class);
        assertThat(columns).doesNotContain("display_name", "password_hash", "consent_at", "consent_version");
    }

    @Test
    void extraFieldsInTheApplicationAreIgnoredAndNotStored() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(signup("extra@school.ac.kr"));
        body.put("password", "attackers-password1");
        body.put("displayName", "공격자");
        body.put("consent", true);

        submit(body).andExpect(status().isAccepted());

        assertThat(jdbc.queryForMap("SELECT * FROM pending_signup").values().toString()).doesNotContain("attackers-password1")
                .doesNotContain("공격자");
        assertThat(pendingRows()).isEqualTo(1);
    }

    @Test
    void alreadyVerifiedEmailIsRejectedEvenWithDifferentCaseAndSpaces() throws Exception {
        submit(signup(" M1@School.AC.KR ")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("ALREADY_REGISTERED"));

        assertThat(pendingRows()).isZero();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void invalidEmailGivesFieldErrorAndLeavesNothingBehind() throws Exception {
        submit(signup("not-an-email")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")));
        submit(signup("   ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")));
        submit(new LinkedHashMap<>()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")));
        submit(signup("a".repeat(250) + "@school.ac.kr")).andExpect(status().isBadRequest());

        assertThat(pendingRows()).isZero();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void emailWhoseLowercaseFormIsLongerThanTheKeyColumnIsRejectedNotAServerError() throws Exception {
        // 'İ'(U+0130)는 소문자로 두 글자가 되어 키가 254자를 넘는다
        submit(signup("İ".repeat(130) + "@school.ac.kr")).andExpect(status().is4xxClientError());
        assertThat(pendingRows()).isZero();
    }

    @Test
    void secondRequestForAnEmailWithAPendingSignupIsLimitedWithoutAnotherMail() throws Exception {
        submit(signup("dup@school.ac.kr")).andExpect(status().isAccepted());

        submit(signup("dup@school.ac.kr")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("TOO_MANY_REQUESTS"));

        assertThat(pendingRows()).isEqualTo(1);
        assertThat(mail.sent()).hasSize(1);
    }

    @Test
    void thereIsNothingToPreemptApplyingTwiceForTheSameEmailKeepsOneRow() throws Exception {
        submit(signup("victim@school.ac.kr")).andExpect(status().isAccepted());
        clock.advance(java.time.Duration.ofMinutes(2));
        submit(signup("VICTIM@school.ac.kr")).andExpect(status().isAccepted());

        assertThat(pendingRows()).isEqualTo(1);
        assertThat(mail.sent()).hasSize(2);
    }
}

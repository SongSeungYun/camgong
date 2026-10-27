package com.camgong.auth;

import static com.camgong.support.AuthBodies.signup;
import static com.camgong.support.GroupBuyBodies.json;
import static com.camgong.support.GroupBuyBodies.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 1, 4, 5 */
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
        submit(signup("new@school.ac.kr", "홍길동", "password1"))
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
    void storesOnlyHashesAndNormalizedKey() throws Exception {
        submit(signup("  Mixed.Case@School.AC.KR ", " 홍길동 ", "password1")).andExpect(status().isAccepted());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM pending_signup");
        assertThat(row.get("email_key")).isEqualTo("mixed.case@school.ac.kr");
        assertThat(row.get("email")).isEqualTo("Mixed.Case@School.AC.KR");
        assertThat(row.get("display_name")).isEqualTo("홍길동");
        assertThat((String) row.get("password_hash")).startsWith("$2").doesNotContain("password1");
        String token = mail.sent().get(0).verifyLink().split("token=")[1];
        assertThat(row.get("token_hash")).isEqualTo(Tokens.sha256Hex(token)).isNotEqualTo(token);
        assertThat(row.get("consent_version")).isEqualTo("v1");
        assertThat(row.get("sent_count")).isEqualTo(1);
        assertThat(row.get("used_at")).isNull();
    }

    @Test
    void alreadyVerifiedEmailIsRejectedEvenWithDifferentCaseAndSpaces() throws Exception {
        submit(signup(" M1@School.AC.KR ")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("ALREADY_REGISTERED"));

        assertThat(pendingRows()).isZero();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void invalidInputGivesFieldErrorsAndLeavesNothingBehind() throws Exception {
        Map<String, Object> ok = signup("v@school.ac.kr");
        submit(with(ok, "password", "short7!")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
        submit(with(ok, "email", "not-an-email")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")));
        submit(with(ok, "displayName", "   ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("displayName")));
        submit(with(ok, "consent", false)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("consent")));
        submit(with(ok, "consentVersion", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("consentVersion")));

        assertThat(pendingRows()).isZero();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void passwordLengthBoundaries() throws Exception {
        submit(with(signup("a@school.ac.kr"), "password", "1234567")).andExpect(status().isBadRequest());   // 7자
        submit(with(signup("b@school.ac.kr"), "password", "12345678")).andExpect(status().isAccepted());    // 8자
        submit(with(signup("c@school.ac.kr"), "password", "가".repeat(24))).andExpect(status().isAccepted()); // 72바이트
        submit(with(signup("d@school.ac.kr"), "password", "가".repeat(24) + "a")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));                      // 73바이트
        submit(with(signup("e@school.ac.kr"), "password", "a".repeat(72))).andExpect(status().isAccepted());
        submit(with(signup("f@school.ac.kr"), "password", "a".repeat(73))).andExpect(status().isBadRequest());

        assertThat(pendingRows()).isEqualTo(3);
    }

    @Test
    void displayNameLengthBoundaries() throws Exception {
        submit(with(signup("a@school.ac.kr"), "displayName", "가")).andExpect(status().isBadRequest());
        submit(with(signup("b@school.ac.kr"), "displayName", "가나")).andExpect(status().isAccepted());
        submit(with(signup("c@school.ac.kr"), "displayName", "가".repeat(20))).andExpect(status().isAccepted());
        submit(with(signup("d@school.ac.kr"), "displayName", "가".repeat(21))).andExpect(status().isBadRequest());
        submit(with(signup("e@school.ac.kr"), "displayName", " 가 ")).andExpect(status().isBadRequest()); // 공백 제거 후 1자
    }

    @Test
    void sameNameCanBeRequestedByDifferentEmailsBeforeVerification() throws Exception {
        submit(signup("x@school.ac.kr", "같은이름", "password1")).andExpect(status().isAccepted());
        submit(signup("y@school.ac.kr", "같은이름", "password1")).andExpect(status().isAccepted());
        assertThat(pendingRows()).isEqualTo(2);
    }

    @Test
    void secondRequestForAnEmailWithAPendingSignupIsRejectedWithoutAnotherMail() throws Exception {
        submit(signup("dup@school.ac.kr", "처음이름", "password1")).andExpect(status().isAccepted());

        submit(signup("dup@school.ac.kr", "다른이름", "password2")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("TOO_MANY_REQUESTS"));

        assertThat(pendingRows()).isEqualTo(1);
        assertThat(mail.sent()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT display_name FROM pending_signup", String.class)).isEqualTo("처음이름");
    }

    @Test
    void passwordOfOnlySpacesIsAcceptedWhenLongEnough() throws Exception {
        submit(with(signup("sp@school.ac.kr"), "password", " ".repeat(8))).andExpect(status().isAccepted());
    }
}

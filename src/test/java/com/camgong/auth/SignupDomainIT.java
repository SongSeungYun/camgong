package com.camgong.auth;

import static com.camgong.support.AuthBodies.signup;
import static com.camgong.support.AuthBodies.verify;
import static com.camgong.support.GroupBuyBodies.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.Cookies;
import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 12, 009 US1(ac.kr 접미사 + 추가 목록). 테스트 설정의 추가 목록은 skku.edu=skku.edu,g.skku.edu;sogang.ac.kr=sogang.ac.kr,sg.ac.kr 이다. */
class SignupDomainIT extends IntegrationTestBase {

    private ResultActions submit(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(signup(email))));
    }

    private ResultActions verifyLast(String displayName) throws Exception {
        String link = mail.sent().get(mail.sent().size() - 1).verifyLink();
        String token = link.substring(link.indexOf("token=") + "token=".length());
        return mockMvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json(verify(token, displayName))));
    }

    private int pendingRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM pending_signup", Integer.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "student@gmail.com",
            "a@stanford.edu",
            "a@ac.kr",
            "a@fakeac.kr",
            "student@evil.school.ac.kr.attacker.com",
            "student@school.ac.kr.evil.com",
            "a@xskku.edu"})
    void nonUniversityDomainsAreRejectedWithNothingStored(String email) throws Exception {
        submit(email).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("대학 메일로만 가입할 수 있습니다."));

        assertThat(pendingRows()).isZero();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void caseAndSurroundingSpacesAreIgnored() throws Exception {
        submit("  Student@SCHOOL.AC.KR ").andExpect(status().isAccepted());

        assertThat(pendingRows()).isEqualTo(1);
        assertThat(mail.sent()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({
            "kim@snu.ac.kr, snu.ac.kr",
            "lee@cs.snu.ac.kr, snu.ac.kr",
            "a@skku.edu, skku.edu",
            "b@g.skku.edu, skku.edu",
            "c@mail.g.skku.edu, skku.edu",
            "d@sg.ac.kr, sogang.ac.kr"})
    void verifyStoresTheSchoolAndEveryMemberResponseCarriesIt(String email, String school) throws Exception {
        submit(email).andExpect(status().isAccepted());
        String session = Cookies.sessionOf(verifyLast("새회원").andExpect(status().isOk())
                .andExpect(jsonPath("$.member.school").value(school))
                .andReturn());

        assertThat(jdbc.queryForObject("SELECT school FROM member WHERE email_key = ?", String.class, email))
                .isEqualTo(school);
        mockMvc.perform(get("/api/auth/me").cookie(new jakarta.servlet.http.Cookie("camgong_session", session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.school").value(school));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(com.camgong.support.AuthBodies.login(email, "password1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.school").value(school));
    }

    @Test
    void aDomainNoLongerAllowedAtVerifyTimeIsRejectedOnTheTokenAndNothingIsConsumed() throws Exception {
        // 신청 뒤 운영자가 추가 목록에서 뺀 상황: 지금 목록에 없는 도메인의 신청 행을 직접 만든다.
        submit("a@school.ac.kr").andExpect(status().isAccepted());
        jdbc.update("UPDATE pending_signup SET email_key = 'a@stanford.edu', email = 'a@stanford.edu'");

        verifyLast("새회원").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("token"))
                .andExpect(jsonPath("$.errors[0].message").value("이 메일로는 더 이상 가입할 수 없습니다."));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE email_key = 'a@stanford.edu'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT used_at IS NULL FROM pending_signup", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_session", Integer.class)).isZero();
    }
}

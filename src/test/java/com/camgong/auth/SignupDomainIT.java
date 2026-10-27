package com.camgong.auth;

import static com.camgong.support.AuthBodies.signup;
import static com.camgong.support.GroupBuyBodies.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 12 */
class SignupDomainIT extends IntegrationTestBase {

    private ResultActions submit(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(signup(email))));
    }

    private int pendingRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM pending_signup", Integer.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "student@gmail.com",
            "student@evil.school.ac.kr.attacker.com",
            "student@school.ac.kr.evil.com",
            "student@sub.school.ac.kr",
            "student@xschool.ac.kr",
            "a@b@school.ac.kr"})
    void domainOutsideTheAllowListIsRejectedWithNothingStored(String email) throws Exception {
        submit(email).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")));

        assertThat(pendingRows()).isZero();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void caseAndSurroundingSpacesAreIgnored() throws Exception {
        submit("  Student@SCHOOL.AC.KR ").andExpect(status().isAccepted());

        assertThat(pendingRows()).isEqualTo(1);
        assertThat(mail.sent()).hasSize(1);
    }
}

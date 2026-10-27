package com.camgong.auth;

import static com.camgong.support.AuthBodies.login;
import static com.camgong.support.AuthBodies.signup;
import static com.camgong.support.GroupBuyBodies.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.camgong.support.Cookies;
import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 8, 9, 10, 11, 18 */
class LoginLogoutIT extends IntegrationTestBase {

    @BeforeEach
    void member() {
        seedLoginMember(10, "Login.User@school.ac.kr", "로그인유저", "password1");
    }

    private MvcResult loginOk(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(login(email, password)))).andExpect(status().isOk()).andReturn();
    }

    private MvcResult loginFail(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(login(email, password)))).andExpect(status().isUnauthorized()).andReturn();
    }

    private static Cookie cookie(String value) {
        return new Cookie("camgong_session", value);
    }

    private int sessions() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM login_session", Integer.class);
    }

    @Test
    void loginSetsCookieAndMeReturnsOnlyIdAndName() throws Exception {
        MvcResult result = loginOk("login.user@school.ac.kr", "password1");

        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("HttpOnly").contains("SameSite=Lax");
        mockMvc.perform(get("/api/auth/me").cookie(cookie(Cookies.sessionOf(result))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.id").value(10))
                .andExpect(jsonPath("$.member.displayName").value("로그인유저"))
                .andExpect(jsonPath("$.member.email").doesNotExist());
    }

    @Test
    void emailIsMatchedIgnoringCaseAndSurroundingSpaces() throws Exception {
        loginOk("  LOGIN.USER@SCHOOL.AC.KR ", "password1");
    }

    @Test
    void logoutEndsOnlyThatSessionAndIsIdempotent() throws Exception {
        String phone = Cookies.sessionOf(loginOk("login.user@school.ac.kr", "password1"));
        String laptop = Cookies.sessionOf(loginOk("login.user@school.ac.kr", "password1"));
        assertThat(sessions()).isEqualTo(2);

        MvcResult out = mockMvc.perform(post("/api/auth/logout").cookie(cookie(phone)))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(out.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");

        mockMvc.perform(get("/api/auth/me").cookie(cookie(phone))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/auth/me").cookie(cookie(laptop))).andExpect(status().isOk());
        assertThat(sessions()).isEqualTo(1);

        mockMvc.perform(post("/api/auth/logout").cookie(cookie(phone))).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());   // 로그인하지 않아도 204
    }

    @Test
    void wrongPasswordUnknownEmailAndUnverifiedEmailLookExactlyAlike() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(signup("pending@school.ac.kr", "신청만", "password1")))).andExpect(status().isAccepted());

        String wrongPassword = loginFail("login.user@school.ac.kr", "wrong-password").getResponse().getContentAsString();
        String unknownEmail = loginFail("nobody@school.ac.kr", "password1").getResponse().getContentAsString();
        String unverified = loginFail("pending@school.ac.kr", "password1").getResponse().getContentAsString();

        assertThat(wrongPassword).contains("INVALID_CREDENTIALS");
        assertThat(unknownEmail).isEqualTo(wrongPassword);
        assertThat(unverified).isEqualTo(wrongPassword);
        assertThat(sessions()).isZero();
    }

    @Test
    void loginRequiresBothFields() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "login.user@school.ac.kr")))).andExpect(status().isBadRequest());
    }

    @Test
    void overlongPasswordIsJustWrongCredentialsNotAServerError() throws Exception {
        String tooLong = "a".repeat(73);
        String body = loginFail("login.user@school.ac.kr", tooLong).getResponse().getContentAsString();
        assertThat(body).contains("INVALID_CREDENTIALS");
        assertThat(loginFail("nobody@school.ac.kr", tooLong).getResponse().getContentAsString()).isEqualTo(body);
    }
}

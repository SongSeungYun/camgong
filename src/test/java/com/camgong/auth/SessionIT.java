package com.camgong.auth;

import static com.camgong.support.AuthBodies.login;
import static com.camgong.support.GroupBuyBodies.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.camgong.support.Cookies;
import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 17: HTTP 경로로 본 슬라이딩 만료 */
class SessionIT extends IntegrationTestBase {

    private Cookie cookie;

    @BeforeEach
    void loggedIn() throws Exception {
        seedLoginMember(10, "s@school.ac.kr", "세션유저", "password1");
        var result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(login("s@school.ac.kr", "password1")))).andExpect(status().isOk()).andReturn();
        cookie = new Cookie("camgong_session", Cookies.sessionOf(result));
    }

    private LocalDateTime lastUsed() {
        return jdbc.queryForObject("SELECT last_used_at FROM login_session", LocalDateTime.class);
    }

    @Test
    void activeSessionSlidesAndIdleSessionExpires() throws Exception {
        clock.advance(Duration.ofDays(13));
        mockMvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk());
        assertThat(lastUsed()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

        clock.advance(Duration.ofDays(13));
        mockMvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk());

        clock.advance(Duration.ofDays(15));
        mockMvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_session", Integer.class)).isZero();
    }

    @Test
    void lastUsedIsOnlyTouchedAfterTheInterval() throws Exception {
        LocalDateTime issued = lastUsed();
        clock.advance(Duration.ofMinutes(5));
        mockMvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk());
        assertThat(lastUsed()).isEqualTo(issued);

        clock.advance(Duration.ofMinutes(6));
        mockMvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk());
        assertThat(lastUsed()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    @Test
    void cookieHoldsTheRawTokenAndTheDatabaseOnlyItsHash() {
        String stored = jdbc.queryForObject("SELECT token_hash FROM login_session", String.class);
        assertThat(stored).hasSize(64).isEqualTo(Tokens.sha256Hex(cookie.getValue())).isNotEqualTo(cookie.getValue());
    }

    /** 서버의 14일 슬라이딩에 쿠키 수명이 맞아야 한다. 안 그러면 매일 쓰는 사람도 브라우저가 14일 뒤 쿠키를 버린다. */
    @Test
    void cookieIsReissuedOnlyWhenTheSessionWasRefreshed() throws Exception {
        clock.advance(Duration.ofMinutes(5));
        var early = mockMvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk()).andReturn();
        assertThat(early.getResponse().getHeader("Set-Cookie")).isNull();

        clock.advance(Duration.ofMinutes(6));
        var refreshed = mockMvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk()).andReturn();
        assertThat(refreshed.getResponse().getHeader("Set-Cookie"))
                .startsWith("camgong_session=" + cookie.getValue())
                .contains("Max-Age=1209600").contains("HttpOnly").contains("SameSite=Lax");
    }

    @Test
    void logoutAfterRefreshStillSendsTheClearingCookie() throws Exception {
        clock.advance(Duration.ofMinutes(11));
        var out = mockMvc.perform(post("/api/auth/logout").cookie(cookie)).andExpect(status().isNoContent()).andReturn();
        assertThat(out.getResponse().getHeaders("Set-Cookie")).hasSize(1);
        assertThat(out.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
    }
}

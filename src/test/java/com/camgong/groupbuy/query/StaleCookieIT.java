package com.camgong.groupbuy.query;

import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import com.camgong.support.IntegrationTestBase;

/** FR-001/FR-008: 만료되었거나 위조된 세션 쿠키는 로그인하지 않은 것과 같다 — 목록은 익명으로 200, 상세·내 활동은 401. */
class StaleCookieIT extends IntegrationTestBase {

    private Cookie cookie(String value) {
        return new Cookie("camgong_session", value);
    }

    @Test
    void forgedCookieIsAnonymousForTheListAndUnauthenticatedForDetailAndMyActivity() throws Exception {
        long id = groupBuy().insert(jdbc);
        Cookie forged = cookie("forged-" + "x".repeat(40));

        mockMvc.perform(get("/api/groupbuys").cookie(forged)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));
        mockMvc.perform(get("/api/groupbuys/" + id).cookie(forged)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/groupbuys?role=organizer").cookie(forged)).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredSessionBehavesTheSame() throws Exception {
        long id = groupBuy().insert(jdbc);
        // as() 가 세션 행을 만들고 쿠키를 붙인다. 그 쿠키를 꺼내 시계를 15일 뒤로 보낸다.
        String raw = "test-session-" + ORGANIZER;
        mockMvc.perform(get("/api/groupbuys/" + id).with(as(ORGANIZER))).andExpect(status().isOk());

        clock.advance(Duration.ofDays(15));

        mockMvc.perform(get("/api/groupbuys").cookie(cookie(raw))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));            // 15일 뒤라 시드의 마감(7일)이 지나 기본 OPEN 목록에서 빠진다
        mockMvc.perform(get("/api/groupbuys/" + id).cookie(cookie(raw))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/groupbuys?role=organizer").cookie(cookie(raw))).andExpect(status().isUnauthorized());
    }
}

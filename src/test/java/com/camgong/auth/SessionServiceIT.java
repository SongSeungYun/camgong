package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;

import com.camgong.support.IntegrationTestBase;

/** T009: 세션 발급·확인·슬라이딩·폐기. HTTP 경로는 T019(SessionIT)가 따로 덮는다. */
class SessionServiceIT extends IntegrationTestBase {

    @Autowired SessionService sessions;

    private LocalDateTime lastUsed() {
        return jdbc.queryForObject("SELECT last_used_at FROM login_session", LocalDateTime.class);
    }

    private static LocalDateTime utc(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private int rows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM login_session", Integer.class);
    }

    @Test
    void issueStoresOnlyTheSha256OfTheToken() {
        String raw = sessions.issue(ORGANIZER);

        assertThat(raw).hasSizeGreaterThanOrEqualTo(43).matches("[A-Za-z0-9_-]+");
        String stored = jdbc.queryForObject("SELECT token_hash FROM login_session", String.class);
        assertThat(stored).hasSize(64).isEqualTo(Tokens.sha256Hex(raw)).isNotEqualTo(raw);
        assertThat(sessions.resolve(raw)).contains(ORGANIZER);
    }

    @Test
    void unknownTokenResolvesToNothing() {
        sessions.issue(ORGANIZER);
        assertThat(sessions.resolve("not-a-real-token")).isEmpty();
    }

    @Test
    void slidingWindowKeepsActiveSessionsAndExpiresIdleOnes() {
        String raw = sessions.issue(ORGANIZER);

        clock.advance(Duration.ofDays(13));
        assertThat(sessions.resolve(raw)).contains(ORGANIZER);
        assertThat(lastUsed()).isEqualTo(utc(clock.instant()));   // 13일째 사용이 창을 밀었다

        clock.advance(Duration.ofDays(13));                        // 첫 발급 후 26일이지만 마지막 사용 후 13일
        assertThat(sessions.resolve(raw)).contains(ORGANIZER);

        clock.advance(Duration.ofDays(15));                        // 마지막 사용 후 15일
        assertThat(sessions.resolve(raw)).isEmpty();
        assertThat(rows()).isZero();                               // 만료 행은 지워진다
    }

    @Test
    void expiresExactlyAtTtl() {
        String raw = sessions.issue(ORGANIZER);
        clock.advance(Duration.ofDays(14).minusSeconds(1));
        assertThat(sessions.resolve(raw)).contains(ORGANIZER);

        String other = sessions.issue(PARTICIPANT);
        clock.advance(Duration.ofDays(14));
        assertThat(sessions.resolve(other)).isEmpty();
    }

    @Test
    void lastUsedIsTouchedOnlyAfterTheTouchInterval() {
        String raw = sessions.issue(ORGANIZER);
        LocalDateTime issuedAt = lastUsed();

        clock.advance(Duration.ofMinutes(5));
        sessions.resolve(raw);
        assertThat(lastUsed()).isEqualTo(issuedAt);                // 10분 안에는 쓰지 않는다

        clock.advance(Duration.ofMinutes(6));                      // 합계 11분
        sessions.resolve(raw);
        assertThat(lastUsed()).isEqualTo(utc(clock.instant()));
    }

    @Test
    void revokeDeletesTheSessionAndIsIdempotent() {
        String raw = sessions.issue(ORGANIZER);
        String keep = sessions.issue(ORGANIZER);                   // 다른 기기

        sessions.revoke(raw);
        sessions.revoke(raw);
        sessions.revoke("never-issued");

        assertThat(sessions.resolve(raw)).isEmpty();
        assertThat(sessions.resolve(keep)).contains(ORGANIZER);
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void cookieAttributes() {
        ResponseCookie c = sessions.cookie("abc");
        assertThat(c.getName()).isEqualTo("camgong_session");
        assertThat(c.getValue()).isEqualTo("abc");
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.getSameSite()).isEqualTo("Lax");
        assertThat(c.getPath()).isEqualTo("/");
        assertThat(c.isSecure()).isFalse();                        // cookie-secure 기본값 false
        assertThat(c.getMaxAge()).isEqualTo(Duration.ofDays(14));

        ResponseCookie gone = sessions.clearCookie();
        assertThat(gone.getName()).isEqualTo("camgong_session");
        assertThat(gone.getMaxAge()).isZero();
        assertThat(gone.isHttpOnly()).isTrue();
    }
}

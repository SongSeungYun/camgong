package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.camgong.support.IntegrationTestBase;

class SessionCleanupIT extends IntegrationTestBase {

    @Autowired
    SessionCleanup cleanup;

    private LocalDateTime ago(Duration d) {
        return LocalDateTime.ofInstant(clock.instant().minus(d), ZoneOffset.UTC);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void session(String hash, Duration lastUsedAgo) {
        jdbc.update("INSERT INTO login_session (token_hash, member_id, created_at, last_used_at) VALUES (?, 1, ?, ?)",
                hash, ago(lastUsedAgo), ago(lastUsedAgo));
    }

    private void attempt(String key, Duration updatedAgo, Duration lockedUntilFromNow) {
        LocalDateTime locked = lockedUntilFromNow == null ? null
                : LocalDateTime.ofInstant(clock.instant().plus(lockedUntilFromNow), ZoneOffset.UTC);
        jdbc.update("INSERT INTO login_attempt (email_key, failed_count, locked_until, updated_at) VALUES (?, 3, ?, ?)",
                key, locked, ago(updatedAgo));
    }

    private void pending(String key, Duration expiredAgo, Duration usedAgo) {
        jdbc.update("INSERT INTO pending_signup (email_key, email, token_hash, token_expires_at, last_sent_at, "
                + "window_started_at, sent_count, used_at, created_at) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)",
                key, key, "0".repeat(60) + key.substring(0, 4), ago(expiredAgo), ago(Duration.ofDays(30)),
                ago(Duration.ofDays(30)), usedAgo == null ? null : ago(usedAgo), ago(Duration.ofDays(30)));
    }

    @Test
    void removesOnlyExpiredSessions() {
        session("a".repeat(64), Duration.ofDays(14).plusSeconds(1));
        session("b".repeat(64), Duration.ofDays(14).minusSeconds(1));

        cleanup.run();

        assertThat(jdbc.queryForList("SELECT token_hash FROM login_session", String.class)).containsExactly("b".repeat(64));
    }

    @Test
    void removesOnlyStaleUnlockedAttempts() {
        attempt("old-open", Duration.ofDays(1).plusSeconds(1), null);
        attempt("old-expired-lock", Duration.ofDays(2), Duration.ofSeconds(-1));
        attempt("old-still-locked", Duration.ofDays(2), Duration.ofMinutes(1));
        attempt("recent", Duration.ofHours(23), null);

        cleanup.run();

        assertThat(jdbc.queryForList("SELECT email_key FROM login_attempt", String.class))
                .containsExactlyInAnyOrder("old-still-locked", "recent");
    }

    @Test
    void removesExpiredOrUsedPendingSignupsOnlyAfterTheirGracePeriod() {
        pending("exp-old@x", Duration.ofDays(7).plusSeconds(1), null);
        pending("exp-recent@x", Duration.ofDays(7).minusSeconds(1), null);
        pending("used-old@x", Duration.ofDays(1), Duration.ofDays(1).plusSeconds(1));
        pending("used-recent@x", Duration.ofDays(1), Duration.ofDays(1).minusSeconds(1));
        pending("live@x", Duration.ofSeconds(-60), null);

        cleanup.run();

        assertThat(jdbc.queryForList("SELECT email_key FROM pending_signup", String.class))
                .containsExactlyInAnyOrder("exp-recent@x", "used-recent@x", "live@x");
        assertThat(count("pending_signup")).isEqualTo(3);
    }
}

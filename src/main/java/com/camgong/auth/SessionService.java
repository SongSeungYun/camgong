package com.camgong.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 로그인 세션(research 결정 9·1). 쿠키에는 원문 토큰, DB에는 SHA-256 hex만 둔다.
 * 유지 기간은 마지막 사용 시각부터 세는 슬라이딩이고, 읽기 요청마다 쓰기가 생기지 않게
 * last_used_at 은 touch 간격이 지났을 때만 갱신한다. 시간 판정은 모두 주입한 Clock.
 */
@Service
public class SessionService {

    public static final String COOKIE_NAME = "camgong_session";

    private final JdbcClient jdbc;
    private final Clock clock;
    private final AuthProperties props;

    public SessionService(JdbcClient jdbc, Clock clock, AuthProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.props = props;
    }

    /** 새 세션을 만들고 쿠키에 담을 원문 토큰을 돌려준다. */
    public String issue(long memberId) {
        String raw = Tokens.newToken();
        LocalDateTime now = utc(Instant.now(clock));
        jdbc.sql("""
                INSERT INTO login_session (token_hash, member_id, created_at, last_used_at)
                VALUES (:hash, :member, :now, :now)
                """)
                .param("hash", Tokens.sha256Hex(raw))
                .param("member", memberId)
                .param("now", now)
                .update();
        return raw;
    }

    /**
     * 로그인용(011 결정 3): 검증한 비밀번호 해시가 아직 그대로일 때만 세션을 넣는다. 이 SELECT 는 회원 행을 공유 잠금으로 읽어(REPEATABLE READ 의
     * INSERT … SELECT) 진행 중인 재설정·탈퇴의 커밋을 기다리고, 그 뒤 해시가 바뀌었으면 아무것도 넣지 않는다. 옛 비밀번호로 로그인하던 요청이
     * 재설정 커밋 직후 세션을 넣는 경쟁을 막는다(세션 생성 시각이 변경 시각보다 늦어 password_changed_at 비교로는 못 막는다).
     *
     * @return 넣었으면 원문 토큰, 해시가 바뀌었으면 empty
     */
    public Optional<String> issueIfPassword(long memberId, String verifiedHash) {
        String raw = Tokens.newToken();
        LocalDateTime now = utc(Instant.now(clock));
        int inserted = jdbc.sql("""
                INSERT INTO login_session (token_hash, member_id, created_at, last_used_at)
                SELECT :hash, id, :now, :now FROM member WHERE id = :member AND password_hash = :verified
                """)
                .param("hash", Tokens.sha256Hex(raw))
                .param("member", memberId)
                .param("verified", verifiedHash)
                .param("now", now)
                .update();
        return inserted == 0 ? Optional.empty() : Optional.of(raw);
    }

    /** 확인된 세션: 회원 id 와, 이번 요청이 last_used_at 을 실제로 갱신했는지(쿠키도 다시 내려줘야 하는지). */
    public record Resolved(long memberId, boolean refreshed) {}

    /** 유효한 세션이면 회원 id. 만료됐으면 행을 지우고 empty. */
    public Optional<Long> resolve(String rawToken) {
        return resolveSession(rawToken).map(Resolved::memberId);
    }

    public Optional<Resolved> resolveSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Instant now = Instant.now(clock);
        // 비밀번호를 바꾸기(011) 전에 만든 세션은 없는 세션이다: 재설정 커밋 뒤 세션 삭제가 실패하거나 놓쳐도 옛 세션을 쓸 수 없다.
        Optional<Row> found = jdbc.sql("""
                SELECT s.id, s.member_id, s.last_used_at, s.created_at < m.password_changed_at AS stale
                FROM login_session s JOIN member m ON m.id = s.member_id WHERE s.token_hash = :hash""")
                .param("hash", Tokens.sha256Hex(rawToken))
                .query((rs, i) -> new Row(rs.getLong("id"), rs.getLong("member_id"),
                        rs.getObject("last_used_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), rs.getBoolean("stale")))
                .optional();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Row row = found.get();
        if (row.stale()) {
            // 지우기는 최선 노력이다: 재설정의 member_id 기준 삭제와 겹치면 교착에 질 수 있다. 져도 이 요청은 비로그인이다.
            try {
                jdbc.sql("DELETE FROM login_session WHERE id = :id").param("id", row.id()).update();
            } catch (org.springframework.dao.DataAccessException e) {
                // 남은 행은 다음 요청이나 SessionCleanup(TTL)이 지운다
            }
            return Optional.empty();
        }
        if (!row.lastUsedAt().plus(props.sessionTtl()).isAfter(now)) {
            // last_used_at 까지 맞춰야 읽은 뒤 다른 요청이 갱신한 행을 지우지 않는다.
            jdbc.sql("DELETE FROM login_session WHERE id = :id AND last_used_at = :seen")
                    .param("id", row.id())
                    .param("seen", utc(row.lastUsedAt()))
                    .update();
            return Optional.empty();
        }
        // 조건부 UPDATE: 동시 요청이 와도 간격 안에서는 쓰기가 없고, 겹쳐도 같은 값으로 끝난다.
        int touched = jdbc.sql("UPDATE login_session SET last_used_at = :now WHERE id = :id AND last_used_at < :threshold")
                .param("now", utc(now))
                .param("id", row.id())
                .param("threshold", utc(now.minus(props.sessionTouchInterval())))
                .update();
        return Optional.of(new Resolved(row.memberId(), touched > 0));
    }

    /** 해당 세션만 삭제한다. 없어도 오류가 아니다(멱등). */
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        jdbc.sql("DELETE FROM login_session WHERE token_hash = :hash")
                .param("hash", Tokens.sha256Hex(rawToken))
                .update();
    }

    public ResponseCookie cookie(String rawToken) {
        return base(rawToken).maxAge(props.sessionTtl()).build();
    }

    public ResponseCookie clearCookie() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Lax")
                .path("/");
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record Row(long id, long memberId, Instant lastUsedAt, boolean stale) {}
}

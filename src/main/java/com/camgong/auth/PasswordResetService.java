package com.camgong.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.camgong.common.ApiException;

/**
 * 비밀번호 재설정(011). 요청은 가입 여부와 상관없이 같은 202·같은 한도로 답하고 메일은 회원에게만, 커밋 뒤 메일 실행기에서 보낸다(결정 1·2).
 * 확인은 비밀번호를 바꾸고 로그인 실패 잠금을 풀며 세션을 끝낸다(결정 3). 로그에 이메일을 남기지 않는다(결정 8).
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final Duration WINDOW = Duration.ofHours(24);

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AuthProperties props;
    private final SignupService signups;
    private final TransactionTemplate tx;

    PasswordResetService(JdbcClient jdbc, PasswordEncoder encoder, ApplicationEventPublisher events, Clock clock,
            AuthProperties props, SignupService signups, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.events = events;
        this.clock = clock;
        this.props = props;
        this.signups = signups;
        this.tx = new TransactionTemplate(transactionManager);
    }

    private record Member(long id, String email) {}

    private record Reset(String emailKey, Long memberId, Instant expiresAt, Instant usedAt) {}

    @Transactional
    public PasswordResetResponse request(PasswordResetRequest r) {
        String emailKey = signups.universityKey(r.email());
        Instant now = Instant.now(clock);
        // 회원이 아니어도 토큰을 만든다: 두 경로가 하는 일을 같게 둔다. 탈퇴한 회원은 email_key 가 NULL 이라 여기서 안 보인다.
        String token = Tokens.newToken();
        // 회원이면 재설정 행보다 회원 행을 먼저 잡는다(FOR SHARE, PK). 탈퇴·확인과 같은 순서다: 재설정 행을 먼저 잡으면 FK 확인이 탈퇴가 쥔 회원
        // 행을 기다리고 탈퇴는 이 행을 지우려 기다려 교착한다(회원 주소만 500). 잠근 뒤 다시 읽으므로 그 사이 탈퇴했으면 회원이 아닌 주소로 다룬다.
        // 회원이 아니면 잠그지 않는다(없는 키의 잠금 읽기는 이메일 인덱스 간격을 잠가 가입을 막는다).
        Member member = jdbc.sql("SELECT id FROM member WHERE email_key = :key")
                .param("key", emailKey).query(Long.class).optional()
                .flatMap(id -> jdbc.sql("SELECT id, email FROM member WHERE id = :id AND email_key = :key FOR SHARE")
                        .param("id", id).param("key", emailKey)
                        .query((rs, i) -> new Member(rs.getLong("id"), rs.getString("email"))).optional())
                .orElse(null);
        Long memberId = member == null ? null : member.id();
        String tokenHash = member == null ? null : Tokens.sha256Hex(token);
        LocalDateTime expires = member == null ? null : utc(now.plus(TOKEN_TTL));

        // 가입 신청과 같은 모양(SignupService): 중복이면 예외 대신 행 잠금을 잡고 0행, 그 뒤 한도를 조건부 UPDATE 한 문장으로 판정한다.
        int inserted = jdbc.sql("""
                INSERT INTO password_reset (email_key, member_id, token_hash, token_expires_at, used_at, last_sent_at,
                    window_started_at, sent_count, created_at)
                VALUES (:key, :member, :hash, :expires, NULL, :now, :now, 1, :now)
                ON DUPLICATE KEY UPDATE email_key = email_key
                """)
                .param("key", emailKey).param("member", memberId).param("hash", tokenHash).param("expires", expires)
                .param("now", utc(now))
                .update();
        if (inserted == 0 && refreshQuota(emailKey, memberId, tokenHash, expires, now) == 0) {
            throw limited(emailKey, now);
        }
        if (member != null) {
            events.publishEvent(new PasswordResetMailRequested(member.email(), token));
        }
        return PasswordResetResponse.of(now.plus(props.mail().resendInterval()));
    }

    /** 1분 간격과 24시간 5회(가입 인증 메일과 같은 설정). 새 토큰은 이전 토큰을 덮어쓴다(FR-005). */
    // -- ponytail: MySQL 전용 문법(IF, SET 왼쪽부터 평가). SignupService.refreshMailQuota 와 같은 모양
    private int refreshQuota(String emailKey, Long memberId, String tokenHash, LocalDateTime expires, Instant now) {
        return jdbc.sql("""
                UPDATE password_reset
                SET sent_count = IF(window_started_at <= :windowCut, 1, sent_count + 1),
                    window_started_at = IF(window_started_at <= :windowCut, :now, window_started_at),
                    last_sent_at = :now,
                    member_id = :member,
                    token_hash = :hash,
                    token_expires_at = :expires,
                    used_at = NULL
                WHERE email_key = :key
                  AND last_sent_at <= :gapCut
                  AND (window_started_at <= :windowCut OR sent_count < :limit)
                """)
                .param("key", emailKey).param("member", memberId).param("hash", tokenHash).param("expires", expires)
                .param("now", utc(now))
                .param("windowCut", utc(now.minus(WINDOW)))
                .param("gapCut", utc(now.minus(props.mail().resendInterval())))
                .param("limit", props.mail().dailyLimit())
                .update();
    }

    private ApiException limited(String emailKey, Instant now) {
        Object[] row = jdbc.sql("SELECT last_sent_at, window_started_at, sent_count FROM password_reset "
                + "WHERE email_key = :key FOR UPDATE")
                .param("key", emailKey)
                .query((rs, i) -> new Object[] { instant(rs.getObject("last_sent_at", LocalDateTime.class)),
                        instant(rs.getObject("window_started_at", LocalDateTime.class)), rs.getInt("sent_count") })
                .single();
        Instant retryAt = ((Instant) row[0]).plus(props.mail().resendInterval());
        Instant windowEnd = ((Instant) row[1]).plus(WINDOW);
        if ((Integer) row[2] >= props.mail().dailyLimit() && windowEnd.isAfter(retryAt)) {
            retryAt = windowEnd;
        }
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                "재설정 메일을 너무 자주 요청했습니다. 잠시 뒤에 다시 시도해 주세요.").retryAt(retryAt, now);
    }

    /**
     * 새 비밀번호 정하기(결정 3). 토큰·비밀번호 검사 뒤 BCrypt 는 트랜잭션 밖에서 하고, 트랜잭션은 회원 행 FOR UPDATE 부터 잡는다(010 탈퇴와 같은 순서).
     * 같은 링크가 동시에 두 번 오면 뒤의 것은 회원 잠금을 기다린 뒤 토큰 행을 잠금 읽기로 다시 봐서 409 를 받는다.
     */
    public void confirm(PasswordResetConfirmRequest r) {
        if (r.token() == null || r.token().isBlank()) {
            throw invalidToken();
        }
        String tokenHash = Tokens.sha256Hex(r.token());
        Instant now = Instant.now(clock);
        Reset reset = jdbc.sql("SELECT email_key, member_id, token_expires_at, used_at FROM password_reset WHERE token_hash = :hash")
                .param("hash", tokenHash)
                .query((rs, i) -> new Reset(rs.getString("email_key"), rs.getObject("member_id", Long.class),
                        instant(rs.getObject("token_expires_at", LocalDateTime.class)),
                        instant(rs.getObject("used_at", LocalDateTime.class))))
                .optional().filter(x -> x.memberId() != null).orElseThrow(PasswordResetService::invalidToken);
        if (reset.usedAt() != null) {
            throw tokenUsed();
        }
        if (!reset.expiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.GONE, "TOKEN_EXPIRED", "재설정 링크가 만료되었습니다. 다시 요청해 주세요.");
        }
        SignupService.passwordProblem(r.password()).ifPresent(message -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "입력값이 올바르지 않습니다.",
                    List.of(new ApiException.Field("password", message)));
        });
        String passwordHash = encoder.encode(r.password());
        long memberId = reset.memberId();

        tx.executeWithoutResult(s -> {
            boolean active = jdbc.sql("SELECT withdrawn_at IS NULL FROM member WHERE id = :id FOR UPDATE")
                    .param("id", memberId).query(Boolean.class).optional().orElse(false);
            if (!active) {
                throw invalidToken();                            // 그 사이 탈퇴했다
            }
            // 탈퇴가 먼저 커밋됐으면 행이 없고, 새 요청이 토큰을 바꿨으면 해시가 다르다
            Instant usedAt = jdbc.sql("SELECT used_at FROM password_reset WHERE email_key = :key AND token_hash = :hash FOR UPDATE")
                    .param("key", reset.emailKey()).param("hash", tokenHash)
                    .query((rs, i) -> java.util.Optional.ofNullable(instant(rs.getObject("used_at", LocalDateTime.class))))
                    .optional().orElseThrow(PasswordResetService::invalidToken).orElse(null);
            if (usedAt != null) {
                throw tokenUsed();
            }
            // 변경 시각은 회원 행을 잠근 뒤에 읽는다: 그보다 앞서 커밋된 로그인 세션은 모두 이 시각보다 먼저 만들어졌다(세션 조회가 거부한다).
            LocalDateTime at = utc(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
            jdbc.sql("UPDATE member SET password_hash = :hash, password_changed_at = :at WHERE id = :id")
                    .param("hash", passwordHash).param("at", at).param("id", memberId).update();
            jdbc.sql("UPDATE password_reset SET used_at = :at WHERE email_key = :key")
                    .param("at", at).param("key", reset.emailKey()).update();
            jdbc.sql("DELETE FROM login_attempt WHERE email_key = :key").param("key", reset.emailKey()).update();
        });
        log.info("password-reset memberId={}", memberId);
        // 세션은 커밋 뒤에 지운다(트랜잭션 안에서 지우면 로그인의 세션 INSERT 와 교착할 수 있다, 010 결정 5). 놓친 세션은 세션 조회가
        // password_changed_at 보다 먼저 만든 세션이라 받지 않는다.
        try {
            jdbc.sql("DELETE FROM login_session WHERE member_id = :id").param("id", memberId).update();
        } catch (RuntimeException e) {
            log.warn("password-reset-session-cleanup-failed memberId={} reason={}", memberId, e.getClass().getSimpleName());
        }
    }

    private static LocalDateTime utc(Instant t) {
        return LocalDateTime.ofInstant(t, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime t) {
        return t == null ? null : t.toInstant(ZoneOffset.UTC);
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "알 수 없는 재설정 링크입니다.");
    }

    private static ApiException tokenUsed() {
        return new ApiException(HttpStatus.CONFLICT, "TOKEN_USED", "이미 사용된 재설정 링크입니다.");
    }
}

package com.camgong.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.camgong.common.ApiException;
import com.camgong.member.Member;
import com.camgong.member.MemberRepository;

@Service
public class SignupService {

    private static final int BCRYPT_MAX_BYTES = 72;
    private static final int MAX_EMAIL_KEY = 254;   // member.email_key / pending_signup.email_key 컬럼 길이
    private static final Duration WINDOW = Duration.ofHours(24);

    private final JdbcClient jdbc;
    private final MemberRepository members;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AuthProperties props;
    private final SessionService sessions;

    public SignupService(JdbcClient jdbc, MemberRepository members, PasswordEncoder encoder,
            ApplicationEventPublisher events, Clock clock, AuthProperties props, SessionService sessions) {
        this.sessions = sessions;
        this.jdbc = jdbc;
        this.members = members;
        this.encoder = encoder;
        this.events = events;
        this.clock = clock;
        this.props = props;
    }

    /**
     * 가입 신청(research 결정 4·6·8, 변경 B2). 인증 전에는 회원을 만들지 않고 pending_signup 에 이메일과 토큰만 둔다.
     * 이름·비밀번호·동의는 받지 않는다 — 인증 링크를 누른 사람이 verify 에서 정한다(계정 선점 방지).
     */
    @Transactional
    public SignupResponse signup(SignupRequest r) {
        String emailKey = Keys.normalize(r.email());
        if (emailKey.length() > MAX_EMAIL_KEY || !allowedDomain(emailKey)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "입력값이 올바르지 않습니다.",
                    List.of(new ApiException.Field("email", "허용되지 않은 이메일 도메인입니다.")));
        }
        if (members.existsByEmailKey(emailKey)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REGISTERED", "이미 가입된 이메일입니다.");
        }

        Instant now = Instant.now(clock);
        LocalDateTime at = utcTime(now);
        String token = Tokens.newToken();
        // 중복 키를 예외로 받으면 공유 잠금이 남아 동시 요청끼리 UPDATE 에서 교착한다. 중복이면 행 잠금을 잡고 0행을 돌려받는다.
        int inserted = jdbc.sql("""
                INSERT INTO pending_signup (email_key, email, token_hash, token_expires_at, last_sent_at,
                    window_started_at, sent_count, used_at, created_at)
                VALUES (:key, :email, :tokenHash, :expires, :now, :now, 1, NULL, :now)
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("key", emailKey)
                .param("email", r.email())
                .param("now", at)
                .param("tokenHash", Tokens.sha256Hex(token))
                .param("expires", utcTime(now.plus(props.verifyTokenTtl())))
                .update();
        if (inserted == 0) {
            // 이미 신청이 있다: 한도 안이면 토큰만 새로 건다(이전 토큰은 token_hash 교체로 무효).
            if (refreshMailQuota(emailKey, token, now) == 0) {
                throw limited(emailKey, now, false);
            }
        }

        // 메일은 요청에 적힌 문자열이 아니라 DB 에 저장된 주소로 보낸다(키가 같아도 대소문자가 다른 문자열이 수신자가 되지 않게).
        String storedEmail = storedEmail(emailKey);
        events.publishEvent(new VerificationMailRequested(storedEmail, token));
        return SignupResponse.of(storedEmail, now.plus(props.mail().resendInterval()));
    }

    /**
     * 인증 링크 확인(research 결정 3·4·13, 변경 B2). 순서: ① 토큰 조회 → ② 본문 검증(오류면 400, 토큰은 소진되지 않는다)
     * → ③ used_at 조건부 UPDATE 선점 → ④ BCrypt 해시로 회원 INSERT → ⑤ 이름·이메일 충돌이면 예외로 롤백(used_at 도 되돌아가
     * 토큰이 유효한 채로 남는다 — 새 이름으로 다시 보낼 수 있다). 토큰을 한 번만 쓰게 하는 것이 핵심이라 ③ 을 ④ 보다 먼저 한다.
     */
    // ponytail: 인증 링크는 화면이 POST로 확인(메일 스캐너의 GET 미리 열기 방지)
    // ponytail: 가입 입력(이름·비밀번호·동의)은 인증 링크를 누른 사람만 정한다(계정 선점 방지, 변경 B2)
    @Transactional
    public Authenticated verify(VerifyRequest r) {
        Instant now = Instant.now(clock);
        if (r.token() == null || r.token().isBlank()) {
            throw invalidToken();
        }
        PendingSignup p = findByToken(Tokens.sha256Hex(r.token())).orElseThrow(SignupService::invalidToken);
        if (p.usedAt() != null) {
            throw tokenUsed();
        }
        if (!p.tokenExpiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.GONE, "TOKEN_EXPIRED", "인증 링크가 만료되었습니다. 인증 메일을 다시 받아 주세요.");
        }

        String displayName = r.displayName() == null ? null : r.displayName().trim();
        validateSignupInput(r, displayName);
        String passwordHash = encoder.encode(r.password());

        // 동시에 같은 토큰이 와도 영향 행이 1인 쪽만 통과한다(useAffectedRows=true 전제).
        int claimed = jdbc.sql("UPDATE pending_signup SET used_at = :now WHERE id = :id AND used_at IS NULL "
                + "AND token_hash = :hash")   // 읽은 뒤 재발송으로 토큰이 바뀌었으면 옛 토큰으로 소진하지 못한다
                .param("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("id", p.id())
                .param("hash", p.tokenHash())
                .update();
        if (claimed == 0) {
            throw tokenUsed();
        }

        Member member;
        try {
            member = members.saveAndFlush(new Member(displayName, p.email(), passwordHash, now, r.consentVersion(), now));
        } catch (DataIntegrityViolationException e) {
            // 어느 UNIQUE 가 걸렸는지는 MySQL 오류 메시지의 키 이름으로 가른다. 던지면 used_at 도 롤백된다.
            // 메시지 앞쪽의 'Duplicate entry <값>' 에는 사용자 입력이 섞이므로 "for key" 뒤쪽만 본다.
            String message = String.valueOf(e.getMostSpecificCause().getMessage());
            String cause = message.substring(Math.max(0, message.lastIndexOf("for key")));
            if (cause.contains("display_name_key")) {
                throw new ApiException(HttpStatus.CONFLICT, "DISPLAY_NAME_TAKEN",
                        "이미 사용 중인 이름입니다. 다른 이름으로 다시 확인해 주세요.");
            }
            if (cause.contains("email_key")) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REGISTERED", "이미 가입된 이메일입니다.");
            }
            throw e;
        }
        String sessionToken = sessions.issue(member.getId());
        return new Authenticated(new AuthResponse(new MemberView(member.getId(), member.getName())), sessionToken);
    }

    /** 본문 검증 오류를 모아 400 으로 돌려준다. 토큰은 아직 소진되지 않았다. */
    private static void validateSignupInput(VerifyRequest r, String displayName) {
        List<ApiException.Field> errors = new ArrayList<>();
        if (displayName == null || displayName.isEmpty()) {
            errors.add(new ApiException.Field("displayName", "표시 이름을 입력해 주세요."));
        } else if (displayName.length() < 2 || displayName.length() > 20) {
            errors.add(new ApiException.Field("displayName", "표시 이름은 2~20자여야 합니다."));
        } else if (Keys.normalize(displayName).length() > 20) {
            // 소문자 변환이 길이를 늘리는 글자(예: 'İ')는 이름 키 컬럼(20자)을 넘어 저장 오류가 되므로 미리 막는다.
            errors.add(new ApiException.Field("displayName", "사용할 수 없는 글자가 포함된 이름입니다."));
        }
        String password = r.password();
        if (password == null || password.isBlank()) {
            errors.add(new ApiException.Field("password", "비밀번호를 입력해 주세요."));
        } else if (password.length() < 8) {
            errors.add(new ApiException.Field("password", "비밀번호는 8자 이상이어야 합니다."));
        } else if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            // BCrypt 는 72바이트 뒤를 잘라 버리므로 글자 수가 아니라 바이트로 막는다.
            errors.add(new ApiException.Field("password", "비밀번호는 72바이트(영문 72자, 한글 24자) 이하여야 합니다."));
        }
        if (!Boolean.TRUE.equals(r.consent())) {
            errors.add(new ApiException.Field("consent", "개인정보 수집·이용에 동의해야 합니다."));
        }
        if (r.consentVersion() == null || r.consentVersion().isBlank() || r.consentVersion().length() > 20) {
            errors.add(new ApiException.Field("consentVersion", "동의 안내문 버전이 필요합니다."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "입력값이 올바르지 않습니다.", errors);
        }
    }

    /** 인증 메일 재발송. 표시 이름·비밀번호는 그대로 두고 새 토큰만 건다. */
    @Transactional
    public SignupResponse resend(ResendRequest r) {
        String emailKey = Keys.normalize(r.email());
        Instant now = Instant.now(clock);
        String token = Tokens.newToken();
        if (refreshMailQuota(emailKey, token, now) == 0) {
            throw limited(emailKey, now, true);
        }
        String storedEmail = storedEmail(emailKey);
        events.publishEvent(new VerificationMailRequested(storedEmail, token));
        return SignupResponse.of(storedEmail, now.plus(props.mail().resendInterval()));
    }

    /** 저장된 신청 주소(원문). 한도 UPDATE 가 방금 이 행을 잡았으므로 잠금 읽기로 최신 값을 본다. */
    private String storedEmail(String emailKey) {
        return jdbc.sql("SELECT email FROM pending_signup WHERE email_key = :key FOR UPDATE")
                .param("key", emailKey).query(String.class).single();
    }

    /**
     * 메일 한도 판정과 갱신을 조건부 UPDATE 한 문장으로 한다(research 결정 6): 1분 간격과 24시간 5회를 WHERE 가
     * 판정하므로 동시에 와도 행 잠금 뒤에 조건을 다시 보는 한 건만 영향 행 1을 받는다(useAffectedRows=true 전제).
     * SET 은 왼쪽에서 오른쪽으로 평가되어, sent_count 가 바뀌기 전의 window_started_at 을 본다.
     */
    // -- ponytail: MySQL 전용 문법(IF, INTERVAL, SET 평가 순서)
    private int refreshMailQuota(String emailKey, String token, Instant now) {
        return jdbc.sql("""
                UPDATE pending_signup
                SET sent_count = IF(window_started_at <= :windowCut, 1, sent_count + 1),
                    window_started_at = IF(window_started_at <= :windowCut, :now, window_started_at),
                    last_sent_at = :now,
                    token_hash = :tokenHash,
                    token_expires_at = :expires
                WHERE email_key = :key AND used_at IS NULL
                  AND last_sent_at <= :gapCut
                  AND (window_started_at <= :windowCut OR sent_count < :limit)
                """)
                .param("key", emailKey)
                .param("now", utcTime(now))
                .param("windowCut", utcTime(now.minus(WINDOW)))
                .param("gapCut", utcTime(now.minus(props.mail().resendInterval())))
                .param("limit", props.mail().dailyLimit())
                .param("tokenHash", Tokens.sha256Hex(token))
                .param("expires", utcTime(now.plus(props.verifyTokenTtl())))
                .update();
    }

    /** UPDATE 가 0행일 때 이유를 가른다. 잠금 읽기라서 동시에 커밋된 최신 행을 본다. */
    private ApiException limited(String emailKey, Instant now, boolean resend) {
        var row = jdbc.sql("SELECT last_sent_at, window_started_at, sent_count, used_at FROM pending_signup "
                + "WHERE email_key = :key FOR UPDATE")
                .param("key", emailKey)
                .query((rs, i) -> new Object[] { utc(rs.getObject("last_sent_at", LocalDateTime.class)),
                        utc(rs.getObject("window_started_at", LocalDateTime.class)), rs.getInt("sent_count"),
                        rs.getObject("used_at", LocalDateTime.class) })
                .optional();
        if (row.isEmpty() || row.get()[3] != null) {
            // 없음과 이미 인증됨을 구분하지 않는다(가입 여부 노출 방지).
            return resend ? new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "인증 대기 중인 신청이 없습니다.")
                    : new ApiException(HttpStatus.CONFLICT, "ALREADY_REGISTERED", "이미 가입된 이메일입니다.");
        }
        Instant lastSent = (Instant) row.get()[0];
        Instant windowStart = (Instant) row.get()[1];
        int sentCount = (Integer) row.get()[2];
        Instant retryAt = lastSent.plus(props.mail().resendInterval());
        if (sentCount >= props.mail().dailyLimit() && windowStart.plus(WINDOW).isAfter(retryAt)) {
            retryAt = windowStart.plus(WINDOW);
        }
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                "인증 메일을 너무 자주 요청했습니다. 잠시 뒤에 다시 시도해 주세요.").retryAt(retryAt, now);
    }

    // ponytail: 허용 도메인은 설정값(ALLOWED_EMAIL_DOMAINS). 학교가 여럿으로 늘고 관리가 필요해지면 DB 테이블로
    /** 마지막 @ 뒤가 허용 목록과 정확히 같아야 한다. endsWith·contains 는 evil.school.ac.kr 같은 우회를 못 막는다. */
    private boolean allowedDomain(String emailKey) {
        int at = emailKey.lastIndexOf('@');
        if (at <= 0 || emailKey.indexOf('@') != at) {
            return false;
        }
        String domain = emailKey.substring(at + 1);
        return props.allowedEmailDomains().stream().anyMatch(d -> Keys.normalize(d).equals(domain));
    }

    private static LocalDateTime utcTime(Instant t) {
        return LocalDateTime.ofInstant(t, ZoneOffset.UTC);
    }

    private Optional<PendingSignup> findByToken(String tokenHash) {
        return jdbc.sql("SELECT * FROM pending_signup WHERE token_hash = :hash")
                .param("hash", tokenHash)
                .query((rs, i) -> new PendingSignup(rs.getLong("id"), rs.getString("email_key"), rs.getString("email"),
                        rs.getString("token_hash"), utc(rs.getObject("token_expires_at", LocalDateTime.class)),
                        utc(rs.getObject("last_sent_at", LocalDateTime.class)),
                        utc(rs.getObject("window_started_at", LocalDateTime.class)), rs.getInt("sent_count"),
                        utc(rs.getObject("used_at", LocalDateTime.class)),
                        utc(rs.getObject("created_at", LocalDateTime.class))))
                .optional();
    }

    private static Instant utc(LocalDateTime t) {
        return t == null ? null : t.toInstant(ZoneOffset.UTC);
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "알 수 없는 인증 링크입니다.");
    }

    private static ApiException tokenUsed() {
        return new ApiException(HttpStatus.CONFLICT, "TOKEN_USED", "이미 사용된 인증 링크입니다.");
    }
}

package com.camgong.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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
     * 가입 신청(research 결정 4·6·8). 인증 전에는 회원을 만들지 않고 pending_signup 에만 둔다.
     * 표시 이름은 이 시점에 점유·검사하지 않는다(FR-003, FR-006): 인증 시점에 판정한다.
     */
    @Transactional
    public SignupResponse signup(SignupRequest r) {
        // BCrypt 는 72바이트 뒤를 잘라 버리므로 글자 수가 아니라 바이트로 막는다.
        if (r.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "입력값이 올바르지 않습니다.",
                    List.of(new ApiException.Field("password", "비밀번호는 72바이트(영문 72자, 한글 24자) 이하여야 합니다.")));
        }
        String emailKey = Keys.normalize(r.email());
        if (members.existsByEmailKey(emailKey)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REGISTERED", "이미 가입된 이메일입니다.");
        }

        Instant now = Instant.now(clock);
        LocalDateTime at = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        String token = Tokens.newToken();
        try {
            jdbc.sql("""
                    INSERT INTO pending_signup (email_key, email, display_name, password_hash, consent_at,
                        consent_version, token_hash, token_expires_at, last_sent_at, window_started_at,
                        sent_count, used_at, created_at)
                    VALUES (:key, :email, :name, :hash, :now, :consentVersion, :tokenHash, :expires, :now, :now,
                        1, NULL, :now)
                    """)
                    .param("key", emailKey)
                    .param("email", r.email())
                    .param("name", r.displayName())
                    .param("hash", encoder.encode(r.password()))
                    .param("now", at)
                    .param("consentVersion", r.consentVersion())
                    .param("tokenHash", Tokens.sha256Hex(token))
                    .param("expires", LocalDateTime.ofInstant(now.plus(props.verifyTokenTtl()), ZoneOffset.UTC))
                    .update();
        } catch (DuplicateKeyException e) {
            // 이미 신청이 있다. 재제출 갱신은 T031 에서 이 분기를 바꾼다(retryAt 은 T029).
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                    "이미 인증 메일을 보냈습니다. 잠시 뒤에 다시 시도해 주세요.");
        }

        events.publishEvent(new VerificationMailRequested(r.email(), token));
        return SignupResponse.of(r.email(), now.plus(props.mail().resendInterval()));
    }

    /** 인증 성공 결과: 응답 본문과 쿠키에 담을 세션 원문 토큰. */
    public record Verified(AuthResponse response, String sessionToken) {}

    /**
     * 인증 링크 확인(research 결정 3·4). 토큰을 한 번만 쓰게 하는 것이 핵심이라 used_at 조건부 UPDATE 를
     * 회원 INSERT 보다 먼저 실행한다. 이어지는 INSERT 가 실패하면 예외로 같은 트랜잭션이 롤백되어
     * used_at 도 되돌아가므로 토큰은 유효한 채로 남는다(이름 충돌 뒤 새 이름으로 다시 시도할 수 있다).
     */
    // ponytail: 인증 링크는 화면이 POST로 확인(메일 스캐너의 GET 미리 열기 방지)
    @Transactional
    public Verified verify(VerifyRequest r) {
        Instant now = Instant.now(clock);
        PendingSignup p = findByToken(Tokens.sha256Hex(r.token()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "알 수 없는 인증 링크입니다."));
        if (p.usedAt() != null) {
            throw tokenUsed();
        }
        if (!p.tokenExpiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.GONE, "TOKEN_EXPIRED", "인증 링크가 만료되었습니다. 인증 메일을 다시 받아 주세요.");
        }

        // 동시에 같은 토큰이 와도 영향 행이 1인 쪽만 통과한다(useAffectedRows=true 전제).
        int claimed = jdbc.sql("UPDATE pending_signup SET used_at = :now WHERE id = :id AND used_at IS NULL")
                .param("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("id", p.id())
                .update();
        if (claimed == 0) {
            throw tokenUsed();
        }

        String displayName = r.displayName() != null ? r.displayName() : p.displayName();
        Member member;
        try {
            member = members.saveAndFlush(new Member(displayName, p.email(), p.passwordHash(),
                    p.consentAt(), p.consentVersion(), now));
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
        return new Verified(new AuthResponse(new MemberView(member.getId(), member.getName())), sessionToken);
    }

    private Optional<PendingSignup> findByToken(String tokenHash) {
        return jdbc.sql("SELECT * FROM pending_signup WHERE token_hash = :hash")
                .param("hash", tokenHash)
                .query((rs, i) -> new PendingSignup(rs.getLong("id"), rs.getString("email_key"), rs.getString("email"),
                        rs.getString("display_name"), rs.getString("password_hash"),
                        utc(rs.getObject("consent_at", LocalDateTime.class)), rs.getString("consent_version"),
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

    private static ApiException tokenUsed() {
        return new ApiException(HttpStatus.CONFLICT, "TOKEN_USED", "이미 사용된 인증 링크입니다.");
    }
}

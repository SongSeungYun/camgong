package com.camgong.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.camgong.common.ApiException;
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

    public SignupService(JdbcClient jdbc, MemberRepository members, PasswordEncoder encoder,
            ApplicationEventPublisher events, Clock clock, AuthProperties props) {
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
}

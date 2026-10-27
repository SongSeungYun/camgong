package com.camgong.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.camgong.common.ApiException;
import com.camgong.member.Member;
import com.camgong.member.MemberRepository;

@Service
public class LoginService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final MemberRepository members;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final AuthProperties props;
    /** 없는 계정에도 같은 해시 비교를 해서 응답 시간 차이를 줄이기 위한 미리 만든 해시. */
    private final String dummyHash;

    public LoginService(MemberRepository members, PasswordEncoder encoder, SessionService sessions, JdbcClient jdbc,
            Clock clock, AuthProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.props = props;
        this.members = members;
        this.encoder = encoder;
        this.sessions = sessions;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    // ponytail: 계정 존재 여부를 응답·시간으로 드러내지 않는다
    public Authenticated login(LoginRequest r) {
        String emailKey = Keys.normalize(r.email());
        Instant now = Instant.now(clock);
        // 잠금 중이면 비밀번호를 보지 않고 거부한다(없는 계정도 같은 규칙이라 응답이 같다).
        lockedUntil(emailKey).filter(until -> until.isAfter(now)).ifPresent(until -> {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED",
                    "로그인 시도가 너무 많습니다. 잠시 뒤에 다시 시도해 주세요.").retryAt(until, now);
        });
        Optional<Member> found = members.findByEmailKey(emailKey);
        // BCrypt 는 72바이트를 넘는 입력을 다루지 못하므로 그런 비밀번호는 맞을 수 없다.
        boolean comparable = r.password().getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;

        // 인증 전 신청만 있는 이메일도 member 에 없으니 같은 경로를 탄다(tasks 표 b).
        String hash = found.map(Member::getPasswordHash).orElse(dummyHash);
        boolean matches = encoder.matches(comparable ? r.password() : "", hash) && comparable && found.isPresent();
        if (!matches) {
            recordFailure(emailKey, now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        Member member = found.get();
        jdbc.sql("DELETE FROM login_attempt WHERE email_key = :key").param("key", emailKey).update();
        return new Authenticated(new AuthResponse(new MemberView(member.getId(), member.getName())),
                sessions.issue(member.getId()));
    }

    /**
     * 실패를 한 문장으로 원자적으로 센다(research 결정 7). 이 메서드는 트랜잭션 밖에서 불려 바로 커밋되므로
     * 뒤따르는 401 예외에 롤백되지 않는다. SET 은 왼쪽에서 오른쪽으로 평가되어 locked_until 은 갱신된
     * failed_count 를 본다: 잠금이 풀린 뒤의 첫 실패는 1로 돌아가고, 5번째에 잠기며, 이미 잠긴 상태에서
     * 동시에 도착한 실패는 잠금 시각을 늘리지 않는다.
     */
    // -- ponytail: 이메일 키로 센다. 남이 일부러 실패를 쌓아 15분 막을 수 있다(수용), 필요하면 IP 단위 추가
    // -- ponytail: MySQL 전용 문법. VALUES 를 참조하지 않아 별칭(AS new) 문법이 필요 없다
    void recordFailure(String emailKey, Instant now) {
        LocalDateTime at = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        jdbc.sql("""
                INSERT INTO login_attempt (email_key, failed_count, locked_until, updated_at)
                VALUES (:key, 1, NULL, :now)
                ON DUPLICATE KEY UPDATE
                    failed_count = IF(locked_until IS NOT NULL AND locked_until <= :now, 1, failed_count + 1),
                    locked_until = IF(failed_count >= :max,
                        IF(locked_until IS NULL OR locked_until <= :now, :lockedUntil, locked_until),
                        IF(locked_until <= :now, NULL, locked_until)),
                    updated_at = :now
                """)
                .param("key", emailKey)
                .param("now", at)
                .param("max", props.login().maxFailures())
                .param("lockedUntil", LocalDateTime.ofInstant(now.plus(props.login().lockDuration()), ZoneOffset.UTC))
                .update();
    }

    private Optional<Instant> lockedUntil(String emailKey) {
        return jdbc.sql("SELECT locked_until FROM login_attempt WHERE email_key = :key")
                .param("key", emailKey)
                .query((rs, i) -> rs.getObject("locked_until", LocalDateTime.class))
                .optional()
                .map(t -> t.toInstant(ZoneOffset.UTC));
    }
}

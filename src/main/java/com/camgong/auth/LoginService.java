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
    private static final int MAX_EMAIL_KEY = 254;

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
        if (emailKey.length() > MAX_EMAIL_KEY) {
            // 소문자 변환이 길이를 늘려 컬럼(254자)을 넘는 이메일은 존재할 수 없다. 저장하지 않고 다른 실패와 똑같이 답한다.
            encoder.matches(r.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES ? "" : r.password(), dummyHash);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        // 시도를 비밀번호 비교 전에 센다(한 문장으로 원자적): 동시에 같은 이메일로 틀린 요청이 몰려도 잠금 기준(5회)까지만
        // 비교로 들어가고, 이미 잠긴 뒤에 도착한 요청은 BCrypt 없이 바로 거부된다(없는 계정도 같은 규칙이라 응답이 같다).
        if (!countAttempt(emailKey, now)) {
            throw locked(emailKey, now);
        }
        Optional<Member> found = members.findByEmailKey(emailKey);
        // BCrypt 는 72바이트를 넘는 입력을 다루지 못하므로 그런 비밀번호는 맞을 수 없다.
        boolean comparable = r.password().getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;

        // 인증 전 신청만 있는 이메일도 member 에 없으니 같은 경로를 탄다(tasks 표 b).
        String hash = found.map(Member::getPasswordHash).orElse(dummyHash);
        boolean matches = encoder.matches(comparable ? r.password() : "", hash) && comparable && found.isPresent();
        if (!matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        completeSuccess(emailKey);
        Member member = found.get();
        return new Authenticated(new AuthResponse(new MemberView(member.getId(), member.getName())),
                sessions.issue(member.getId()));
    }

    /**
     * 이 시도를 센다(research 결정 7). 트랜잭션 밖의 한 문장이라 바로 커밋된다. SET 은 왼쪽에서 오른쪽으로 평가되므로
     * updated_at 을 맨 앞에 둬 아직 바뀌지 않은 locked_until 을 보게 하고, locked_until 은 갱신된 failed_count 를 본다.
     * 잠금 중이면 아무 것도 바꾸지 않아(영향 행 0) 횟수도 잠금 시각도 늘지 않는다. 잠금이 풀린 뒤의 첫 시도는 1로 돌아가고,
     * 5번째 시도가 곧바로 잠금을 건다.
     *
     * @return 센 시도면 true, 이미 잠겨 있어 세지 않았으면 false
     */
    // -- ponytail: 이메일 키로 센다. 남이 일부러 시도를 쌓아 15분 막을 수 있다(수용), 필요하면 IP 단위 추가
    // -- ponytail: MySQL 전용 문법(영향 행 0/1/2 판정은 useAffectedRows=true 전제). VALUES 를 참조하지 않아 별칭이 필요 없다
    boolean countAttempt(String emailKey, Instant now) {
        LocalDateTime at = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        int rows = jdbc.sql("""
                INSERT INTO login_attempt (email_key, failed_count, locked_until, updated_at)
                VALUES (:key, 1, NULL, :now)
                ON DUPLICATE KEY UPDATE
                    updated_at = IF(locked_until IS NOT NULL AND locked_until > :now, updated_at, :now),
                    failed_count = IF(locked_until IS NOT NULL AND locked_until > :now, failed_count,
                        IF(locked_until IS NOT NULL, 1, failed_count + 1)),
                    locked_until = IF(locked_until IS NOT NULL AND locked_until > :now, locked_until,
                        IF(failed_count >= :max, :lockedUntil, NULL))
                """)
                .param("key", emailKey)
                .param("now", at)
                .param("max", props.login().maxFailures())
                .param("lockedUntil", LocalDateTime.ofInstant(now.plus(props.login().lockDuration()), ZoneOffset.UTC))
                .update();
        return rows != 0;
    }

    /**
     * 비밀번호가 맞았을 때: 횟수를 지운다. 이 시도는 잠금 기준에 닿기 전에 "입장"(countAttempt)한 요청이다 — 입장한 시도만
     * 비밀번호 비교까지 가므로, 한 이메일의 동시 비교는 잠금 창마다 최대 5번이다. 5번째로 입장해 스스로 잠금을 건 올바른 시도도
     * 여기서 횟수를 지운다(4번 틀리고 5번째에 맞으면 성공).
     */
    void completeSuccess(String emailKey) {
        jdbc.sql("DELETE FROM login_attempt WHERE email_key = :key").param("key", emailKey).update();
    }

    private ApiException locked(String emailKey, Instant now) {
        Instant until = lockedUntil(emailKey).filter(t -> t.isAfter(now)).orElse(now);
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED",
                "로그인 시도가 너무 많습니다. 잠시 뒤에 다시 시도해 주세요.").retryAt(until, now);
    }

    private Optional<Instant> lockedUntil(String emailKey) {
        return jdbc.sql("SELECT locked_until FROM login_attempt WHERE email_key = :key")
                .param("key", emailKey)
                .query((rs, i) -> rs.getObject("locked_until", LocalDateTime.class))
                .optional()
                .map(t -> t.toInstant(ZoneOffset.UTC));
    }
}

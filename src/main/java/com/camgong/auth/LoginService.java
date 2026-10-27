package com.camgong.auth;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.http.HttpStatus;
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
    /** 없는 계정에도 같은 해시 비교를 해서 응답 시간 차이를 줄이기 위한 미리 만든 해시. */
    private final String dummyHash;

    public LoginService(MemberRepository members, PasswordEncoder encoder, SessionService sessions) {
        this.members = members;
        this.encoder = encoder;
        this.sessions = sessions;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    // ponytail: 계정 존재 여부를 응답·시간으로 드러내지 않는다
    public Authenticated login(LoginRequest r) {
        Optional<Member> found = members.findByEmailKey(Keys.normalize(r.email()));
        // BCrypt 는 72바이트를 넘는 입력을 다루지 못하므로 그런 비밀번호는 맞을 수 없다.
        boolean comparable = r.password().getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;

        // 인증 전 신청만 있는 이메일도 member 에 없으니 같은 경로를 탄다(tasks 표 b).
        String hash = found.map(Member::getPasswordHash).orElse(dummyHash);
        boolean matches = encoder.matches(comparable ? r.password() : "", hash) && comparable && found.isPresent();
        if (!matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        Member member = found.get();
        return new Authenticated(new AuthResponse(new MemberView(member.getId(), member.getName())),
                sessions.issue(member.getId()));
    }
}

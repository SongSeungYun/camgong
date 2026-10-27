package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 비밀번호·토큰을 가진 record 를 로그나 예외 메시지에 찍어도 값이 새지 않는다. */
class SecretsInToStringTest {

    @Test
    void secretsAreMasked() {
        assertThat(new LoginRequest("a@school.ac.kr", "very-secret-pw")).hasToString("LoginRequest[email=a@school.ac.kr, password=***]");
        String verify = new VerifyRequest("tok-123456", "홍길동", "very-secret-pw", true, "v1").toString();
        assertThat(verify).doesNotContain("very-secret-pw").doesNotContain("tok-123456").contains("홍길동");
        String authenticated = new Authenticated(new AuthResponse(new MemberView(1L, "홍")), "raw-session-token").toString();
        assertThat(authenticated).doesNotContain("raw-session-token");
    }
}

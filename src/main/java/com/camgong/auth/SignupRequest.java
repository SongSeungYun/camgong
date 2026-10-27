package com.camgong.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 신청은 이메일 하나다. 이름·비밀번호·동의는 인증 링크를 누른 사람이 verify 에서 정한다(계정 선점 방지, 변경 B2):
 * 신청에 비밀번호를 받으면 남이 먼저 내 이메일로 신청해 비밀번호를 정해 둘 수 있다.
 */
public record SignupRequest(@NotBlank @Email @Size(max = 254) String email) {

    /** 앞뒤 공백을 지운 값으로 검증·저장한다. */
    public SignupRequest {
        email = email == null ? null : email.trim();
    }
}

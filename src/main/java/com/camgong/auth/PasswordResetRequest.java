package com.camgong.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 재설정 요청(011). 가입 신청과 같은 이메일 검사. */
public record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {

    public PasswordResetRequest {
        email = email == null ? null : email.trim();
    }

    /** 로그·예외 메시지에 주소 전체가 남지 않게 한다. */
    @Override
    public String toString() {
        return "PasswordResetRequest[email=" + (email == null ? null : VerificationMailListener.mask(email)) + "]";
    }
}

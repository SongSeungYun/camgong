package com.camgong.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank String password) {

    /** 로그·예외 메시지에 비밀번호가 새지 않도록 값을 가린다. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}

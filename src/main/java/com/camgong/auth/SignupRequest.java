package com.camgong.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 2, max = 20) String displayName,
        @NotNull @Size(min = 8) String password,
        @AssertTrue(message = "개인정보 수집·이용에 동의해야 합니다.") boolean consent,
        @NotBlank @Size(max = 20) String consentVersion) {

    /** 이메일과 이름은 앞뒤 공백을 지운 값으로 검증·저장한다(공백뿐인 이름은 NotBlank 에 걸린다). */
    public SignupRequest {
        email = email == null ? null : email.trim();
        displayName = displayName == null ? null : displayName.trim();
    }
}

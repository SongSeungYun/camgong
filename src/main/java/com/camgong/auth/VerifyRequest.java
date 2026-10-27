package com.camgong.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** displayName 은 선택이다. 이름이 이미 쓰여 막혔을 때 새 이름으로 다시 보낸다. */
public record VerifyRequest(@NotBlank String token, @Size(min = 2, max = 20) String displayName) {

    public VerifyRequest {
        displayName = displayName == null ? null : displayName.trim();
    }
}

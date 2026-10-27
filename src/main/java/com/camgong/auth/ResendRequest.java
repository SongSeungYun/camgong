package com.camgong.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendRequest(@NotBlank @Email @Size(max = 254) String email) {

    public ResendRequest {
        email = email == null ? null : email.trim();
    }
}

package com.camgong.auth;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 재설정 요청 202. 가입 여부와 상관없이 같은 본문이다. */
public record PasswordResetResponse(OffsetDateTime retryAt) {

    static PasswordResetResponse of(Instant retryAt) {
        return new PasswordResetResponse(retryAt.atOffset(ZoneOffset.ofHours(9)));   // 다른 응답과 같은 KST 고정
    }
}

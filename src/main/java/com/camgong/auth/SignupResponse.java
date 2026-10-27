package com.camgong.auth;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record SignupResponse(String email, OffsetDateTime resendAvailableAt) {

    // ponytail: 응답 오프셋은 KST 고정(001 응답과 같다). 다른 시간대 사용자가 생기면 요청별로 바꾼다.
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    static SignupResponse of(String email, Instant resendAvailableAt) {
        return new SignupResponse(email, resendAvailableAt.atOffset(KST));
    }
}

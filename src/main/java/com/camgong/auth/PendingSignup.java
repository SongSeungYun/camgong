package com.camgong.auth;

import java.time.Instant;

// ponytail: 인증 테이블은 JdbcClient 전용, 영속 엔티티 아님
record PendingSignup(long id, String emailKey, String email, String displayName, String passwordHash,
        Instant consentAt, String consentVersion, String tokenHash, Instant tokenExpiresAt,
        Instant lastSentAt, Instant windowStartedAt, int sentCount, Instant usedAt, Instant createdAt) {
}

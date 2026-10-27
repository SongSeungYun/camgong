package com.camgong.auth;

import java.time.Instant;

// ponytail: 인증 테이블은 JdbcClient 전용, 영속 엔티티 아님
record LoginSession(long id, String tokenHash, long memberId, Instant createdAt, Instant lastUsedAt) {
}

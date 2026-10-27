package com.camgong.groupbuy.join;

import org.springframework.test.context.TestPropertySource;

/** IdempotentJoinIT 를 conditional 게이트로 한 번 더 돌린다: API 동작은 방식과 무관해야 한다. */
@TestPropertySource(properties = "camgong.quantity-gate=conditional")
class ConditionalIdempotentJoinIT extends IdempotentJoinIT {
}

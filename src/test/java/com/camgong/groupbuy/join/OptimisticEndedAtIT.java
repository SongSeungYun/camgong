package com.camgong.groupbuy.join;

import org.springframework.test.context.TestPropertySource;

/** 012 T002 를 낙관적 게이트로. */
@TestPropertySource(properties = "camgong.quantity-gate=optimistic")
class OptimisticEndedAtIT extends EndedAtIT {
}

package com.camgong.groupbuy.join;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "camgong.quantity-gate=conditional")
class QuantityRaceConditionalIT extends QuantityRaceTests {
}

package com.camgong.groupbuy.join;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "camgong.quantity-gate=pessimistic")
class ParticipationSerializationPessimisticIT extends ParticipationSerializationTests {
}

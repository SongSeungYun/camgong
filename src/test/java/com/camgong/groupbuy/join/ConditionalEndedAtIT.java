package com.camgong.groupbuy.join;

import org.springframework.test.context.TestPropertySource;

/** 012 T002 를 조건부 UPDATE 게이트로(모집 완료가 SQL 한 문장에서 일어난다). */
@TestPropertySource(properties = "camgong.quantity-gate=conditional")
class ConditionalEndedAtIT extends EndedAtIT {
}

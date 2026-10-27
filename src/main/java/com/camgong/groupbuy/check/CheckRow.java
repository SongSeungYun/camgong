package com.camgong.groupbuy.check;

import java.time.OffsetDateTime;

/** 체크표의 한 행(개설자를 뺀 참여자). receivedWithoutPayment: 입금 확인 없이 수령만 확인된 행(화면에서 구분해 보인다). */
public record CheckRow(Long memberId, String name, int quantity, long amount, OffsetDateTime paidConfirmedAt,
        OffsetDateTime receivedConfirmedAt, boolean receivedWithoutPayment) {
}

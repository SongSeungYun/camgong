package com.camgong.groupbuy.query;

import java.time.OffsetDateTime;

import com.camgong.groupbuy.GroupBuyCategory;

/** 목록 카드. 개설자 정보와 구매처 링크는 넣지 않는다(상세에서만 로그인 회원에게 보인다). */
public record GroupBuySummary(
        Long id,
        String title,
        String itemName,
        GroupBuyCategory category,
        int unitPrice,
        int securedQuantity,
        int totalQuantity,
        OffsetDateTime deadline,
        DisplayStatus status) {
}

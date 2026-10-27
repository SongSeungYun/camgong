package com.camgong.groupbuy.query;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.camgong.groupbuy.GroupBuyCategory;

/** 내 활동 항목: 목록 카드와 같은 필드 + role=participant 일 때만 내 수량. */
public record MyActivityItem(
        Long id,
        String title,
        String itemName,
        GroupBuyCategory category,
        int unitPrice,
        int securedQuantity,
        int totalQuantity,
        OffsetDateTime deadline,
        DisplayStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer myQuantity) {
}

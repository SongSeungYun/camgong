package com.camgong.groupbuy;

import java.time.OffsetDateTime;

/** 개설·수정 공용 요청 본문. 수치는 누락을 구분하려고 박싱 타입을 쓴다. 제약은 US2에서 추가. */
public record GroupBuyRequest(
        String title,
        String description,
        String itemName,
        Integer unitPrice,
        Integer totalQuantity,
        OffsetDateTime deadline,
        Integer organizerQuantity,
        GroupBuyCategory category,
        String itemUrl,
        String openChatUrl) {
}

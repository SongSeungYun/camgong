package com.camgong.groupbuy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record GroupBuyResponse(
        Long id,
        String title,
        String description,
        String itemName,
        int unitPrice,
        int totalQuantity,
        int securedQuantity,
        int remainingQuantity,
        OffsetDateTime deadline,
        GroupBuyStatus status,
        Long organizerId,
        int organizerQuantity,
        GroupBuyCategory category,
        String itemUrl,
        String openChatUrl,
        OffsetDateTime createdAt) {

    // ponytail: 응답 오프셋은 KST 고정(계약 예시 +09:00). 다른 시간대 사용자가 생기면 요청별로 바꾼다.
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    /** openChatUrl 은 호출자가 가시성을 판단한 뒤 값 또는 null 로 넘긴다. */
    public static GroupBuyResponse from(GroupBuy g, int organizerQuantity, String openChatUrl) {
        return new GroupBuyResponse(
                g.getId(), g.getTitle(), g.getDescription(), g.getItemName(), g.getUnitPrice(),
                g.getTotalQuantity(), g.getSecuredQuantity(), g.getTotalQuantity() - g.getSecuredQuantity(),
                g.getDeadline().atOffset(KST), g.getStatus(), g.getOrganizerId(), organizerQuantity,
                g.getCategory(), g.getItemUrl(), openChatUrl, g.getCreatedAt().atOffset(KST));
    }
}

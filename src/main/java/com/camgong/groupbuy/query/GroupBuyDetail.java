package com.camgong.groupbuy.query;

import java.time.OffsetDateTime;
import java.util.List;

import com.camgong.groupbuy.GroupBuy;
import com.camgong.groupbuy.GroupBuyCategory;

/**
 * 상세 응답. 숨기는 필드(openChatUrl, participants, myParticipation)는 null 로 내려간다.
 * 006 이 myParticipation 의 확인 시각과 명단의 확인 상태를 이 응답에 더한다.
 */
public record GroupBuyDetail(
        Long id,
        String title,
        String description,
        String itemName,
        GroupBuyCategory category,
        String itemUrl,
        int unitPrice,
        int totalQuantity,
        int securedQuantity,
        int remainingQuantity,
        OffsetDateTime deadline,
        DisplayStatus status,
        String organizerName,
        boolean isOrganizer,
        int participantCount,
        MyParticipationView myParticipation,
        String openChatUrl,
        List<ParticipantView> participants,
        OffsetDateTime createdAt) {

    static GroupBuyDetail of(GroupBuy g, DisplayStatus status, String organizerName, boolean isOrganizer,
            int participantCount, MyParticipationView mine, String openChatUrl, List<ParticipantView> participants) {
        return new GroupBuyDetail(g.getId(), g.getTitle(), g.getDescription(), g.getItemName(), g.getCategory(),
                g.getItemUrl(), g.getUnitPrice(), g.getTotalQuantity(), g.getSecuredQuantity(),
                g.getTotalQuantity() - g.getSecuredQuantity(), GroupBuyQueryService.toOffset(g.getDeadline()), status,
                organizerName, isOrganizer, participantCount, mine, openChatUrl, participants,
                GroupBuyQueryService.toOffset(g.getCreatedAt()));
    }
}

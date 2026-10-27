package com.camgong.groupbuy;

import java.util.List;

/** 커밋 후(AFTER_COMMIT)에만 전달되는 내부 이벤트. 알림 기능이 같은 이벤트를 소비한다. */
public final class GroupBuyEvents {

    private GroupBuyEvents() {
    }

    /** recipientIds: 개설자를 뺀 참여자. changedFields: 요청 본문의 필드 이름(openChatUrl 제외). */
    public record GroupBuyChanged(Long groupBuyId, List<Long> recipientIds, List<String> changedFields) {
    }

    /** recipientIds: 개설자를 뺀 참여자. */
    public record GroupBuyCancelled(Long groupBuyId, List<Long> recipientIds) {
    }

    /** recipientIds: 개설자를 포함한 모든 참여자. */
    public record GroupBuyCompleted(Long groupBuyId, List<Long> recipientIds) {
    }
}

package com.camgong.groupbuy;

import java.util.List;

/** 커밋 후(AFTER_COMMIT)에만 전달되는 내부 이벤트. 알림 기능이 같은 이벤트를 소비한다. */
public final class GroupBuyEvents {

    private GroupBuyEvents() {
    }

    /**
     * recipientIds: 개설자를 뺀 참여자. changedFields: 요청 본문의 필드 이름(openChatUrl 제외).
     * eventId 는 발행 때 한 번 만든 UUID, groupBuyTitle 은 사건 시점의 제목 스냅샷(수정은 수정 후 제목)이다 — 알림 기능이 중복 방지·표시에 쓴다.
     */
    public record GroupBuyChanged(String eventId, Long groupBuyId, String groupBuyTitle, List<Long> recipientIds,
            List<String> changedFields) {
    }

    /** recipientIds: 개설자를 뺀 참여자. groupBuyTitle: 취소된 공구는 이후 읽을 수 없어서 사건 시점에 담는다. */
    public record GroupBuyCancelled(String eventId, Long groupBuyId, String groupBuyTitle, List<Long> recipientIds) {
    }

    /** recipientIds: 개설자를 포함한 모든 참여자. */
    public record GroupBuyCompleted(String eventId, Long groupBuyId, String groupBuyTitle, List<Long> recipientIds) {
    }

    /**
     * 마감 확정으로 모집 실패가 된 공구. recipientIds: 개설자를 포함한 모든 참여 행의 회원 id(중복 없음).
     * eventId 는 발행 때 한 번 만든 값, groupBuyTitle 은 사건 시점의 제목이다(알림 기능이 중복 방지·표시에 쓴다).
     */
    public record GroupBuyFailed(String eventId, Long groupBuyId, String groupBuyTitle, List<Long> recipientIds) {
    }
}

package com.camgong.notification;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 알림 목록·읽음 응답의 항목. 제목·종류·바뀐 항목 이름·시각뿐이고 다른 회원의 정보(이름, 수량, 링크, 이메일)는 어디에도 없다.
 * linkable: 공구 상세로 이동할 수 있는지(취소된 공구는 열 수 없어 GROUPBUY_CANCELLED 만 false).
 */
public record NotificationView(Long id, NotificationType type, Long groupBuyId, String groupBuyTitle,
        List<String> changedFields, OffsetDateTime createdAt, boolean read, boolean linkable) {
}

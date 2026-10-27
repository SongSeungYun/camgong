package com.camgong.notification;

import java.time.Instant;

/** 만들어질 알림 한 건. changedFields 는 저장 형태(쉼표로 이은 항목 이름)다. */
record NotificationRow(String eventId, Long recipientId, NotificationType type, Long groupBuyId, String groupBuyTitle,
        String changedFields, Instant createdAt) {
}

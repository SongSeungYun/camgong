package com.camgong.notification;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.camgong.support.IntegrationTestBase;

/** 알림 테스트 공통: 공구·참여 시드와 알림 행 조회/삽입. 기본 테스트는 알림을 요청 스레드에서 바로 만든다(application-test.yml). */
abstract class NotificationTestBase extends IntegrationTestBase {

    /** 개설 API 로 연 공구(총 24, 개설자 9, 개당 1,200원) + 참여자 2(3개), 3(2개). 확보 14, 남은 10. */
    protected long openWithParticipants() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 3);
        seedParticipant(id, OUTSIDER, 2);
        return id;
    }

    protected void participate(long groupBuyId, long memberId, int quantity) {
        jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, ?, ?, ?)",
                groupBuyId, memberId, quantity, LocalDateTime.ofInstant(START, ZoneOffset.UTC));
    }

    protected List<Map<String, Object>> notifications() {
        return jdbc.queryForList("SELECT * FROM notification ORDER BY id");
    }

    protected List<Long> recipientsOf(String type) {
        return jdbc.queryForList("SELECT recipient_id FROM notification WHERE type = ? ORDER BY recipient_id", Long.class, type);
    }

    protected int notificationCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification", Integer.class);
    }

    protected long insertNotification(long recipient, String type, Instant createdAt, boolean read) {
        jdbc.update("INSERT INTO notification (event_id, recipient_id, type, group_buy_id, group_buy_title, changed_fields, created_at, read_at) "
                + "VALUES (UUID(), ?, ?, 1, '시드 알림', ?, ?, ?)", recipient, type, type.equals("GROUPBUY_CHANGED") ? "unitPrice,deadline" : null,
                LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC), read ? LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC) : null);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}

package com.camgong.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 010: 탈퇴한 회원에게는 새 알림을 쓰지 않는다(공구 완료·취소 직후 탈퇴하면 비동기 알림이 늦게 올 수 있다). */
class NotificationWithdrawnRecipientIT extends NotificationTestBase {

    @Autowired
    NotificationWriter writer;

    private void markWithdrawn(long memberId) {
        jdbc.update("UPDATE member SET withdrawn_at = ? WHERE id = ?", LocalDateTime.ofInstant(START, ZoneOffset.UTC), memberId);
    }

    @Test
    void aLateEventSkipsWithdrawnRecipients() throws Exception {
        long id = openWithParticipants();
        markWithdrawn(OUTSIDER);

        cancelAs(ORGANIZER, id).andExpect(status().isNoContent());

        assertThat(recipientsOf("GROUPBUY_CANCELLED")).containsExactly(PARTICIPANT);
    }

    @Test
    void bothTheBatchAndTheRowByRowPathSkipThem() {
        markWithdrawn(OUTSIDER);
        writer.write(List.of(
                new NotificationRow("e1", PARTICIPANT, NotificationType.GROUPBUY_COMPLETED, 1L, "t", null, START),
                new NotificationRow("e1", OUTSIDER, NotificationType.GROUPBUY_COMPLETED, 1L, "t", null, START)));
        writer.insertOne(new NotificationRow("e2", OUTSIDER, NotificationType.GROUPBUY_COMPLETED, 1L, "t", null, START));

        assertThat(jdbc.queryForList("SELECT recipient_id FROM notification", Long.class)).containsExactly(PARTICIPANT);
    }
}

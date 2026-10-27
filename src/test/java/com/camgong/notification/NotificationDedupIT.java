package com.camgong.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCompleted;
import com.camgong.support.Concurrent;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
/** quickstart 시나리오 12, 13, 14 (SC-003). 반복은 -Drace.repeat (CI 기본 20, 수동 1,000). */
class NotificationDedupIT extends NotificationTestBase {

    private static final int REPEAT = Integer.getInteger("race.repeat", 20);

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    PlatformTransactionManager txManager;

    /** 커밋되는 트랜잭션 안에서 이벤트를 발행해 AFTER_COMMIT 리스너가 돌게 한다. */
    private void publishCommitted(GroupBuyCompleted event) {
        new TransactionTemplate(txManager).executeWithoutResult(s -> publisher.publishEvent(event));
    }

    @Test
    void theSameEventProcessedTwiceInARowCreatesOneNotificationPerRecipientQuietly(
            org.springframework.boot.test.system.CapturedOutput output) {
        var event = new GroupBuyCompleted(UUID.randomUUID().toString(), 1L, "t", List.of(ORGANIZER, PARTICIPANT, OUTSIDER));
        publishCommitted(event);
        publishCommitted(event);

        assertThat(notificationCount()).isEqualTo(3);
        // 중복은 오류 없이 무시된다(ON DUPLICATE KEY UPDATE id = id): 실패 경고도, 단건 재시도도 없다.
        assertThat(output.getAll()).doesNotContain("notification-failed").doesNotContain("notification-write-failed");
    }

    @Test
    void theSameEventProcessedConcurrentlyCreatesOneNotificationPerRecipient() throws Exception {
        for (int round = 0; round < REPEAT; round++) {
            jdbc.update("DELETE FROM notification");
            var event = new GroupBuyCompleted(UUID.randomUUID().toString(), 1L, "t", List.of(ORGANIZER, PARTICIPANT, OUTSIDER));
            List<Callable<Boolean>> calls = List.of(
                    () -> { publishCommitted(event); return true; },
                    () -> { publishCommitted(event); return true; },
                    () -> { publishCommitted(event); return true; });
            Concurrent.all(calls);

            assertThat(notificationCount()).as("round %d", round).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT recipient_id) FROM notification", Integer.class)).isEqualTo(3);
        }
    }

    @Test
    void differentEventsForTheSameGroupBuyEachCreateTheirOwnNotifications() {
        publishCommitted(new GroupBuyCompleted("e-1", 1L, "t", List.of(PARTICIPANT)));
        publishCommitted(new GroupBuyCompleted("e-2", 1L, "t", List.of(PARTICIPANT)));
        assertThat(notificationCount()).isEqualTo(2);
    }

    @Test
    void aMemberWhoJoinedAndLeftNeverGetsNotifiedAndNeitherDoesSomeoneWhoNeverJoined() throws Exception {
        long id = openWithParticipants();                                          // 확보 14, 남은 10
        seedMembers(4, 5);
        mockMvc.perform(post("/api/groupbuys/" + id + "/participations").with(as(4)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1,\"expectedUnitPrice\":1200}")).andExpect(status().isCreated());
        mockMvc.perform(delete("/api/groupbuys/" + id + "/participations/me").with(as(4))).andExpect(status().isNoContent());

        cancelAs(ORGANIZER, id).andExpect(status().isNoContent());

        assertThat(recipientsOf("GROUPBUY_CANCELLED")).containsExactly(PARTICIPANT, OUTSIDER);      // 4 는 나갔고 5 는 참여한 적 없다
    }
}

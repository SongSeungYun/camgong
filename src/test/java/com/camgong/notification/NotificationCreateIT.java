package com.camgong.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.camgong.groupbuy.close.GroupBuyCloseService;
import com.camgong.support.GroupBuyBodies;

/** quickstart 시나리오 1, 2, 3, 4, 5, 6, 17, 21, 22 — 실제 발행 지점(001·004·005)을 거쳐 알림이 만들어지는지(T011 연결 확인 포함) */
class NotificationCreateIT extends NotificationTestBase {

    @Autowired
    GroupBuyCloseService closeService;

    @Test
    void theLastSeatNotifiesTheOrganizerAndEveryParticipantOnce() throws Exception {
        long id = openWithParticipants();                                          // 확보 14, 남은 10
        seedMembers(4, 4);
        mockMvc.perform(post("/api/groupbuys/" + id + "/participations").with(as(4)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":10,\"expectedUnitPrice\":1200}")).andExpect(status().isCreated());

        assertThat(recipientsOf("GROUPBUY_COMPLETED")).containsExactly(ORGANIZER, PARTICIPANT, OUTSIDER, 4L);
        assertThat(notificationCount()).isEqualTo(4);
        assertThat(notifications()).allSatisfy(n -> {
            assertThat(n.get("group_buy_title")).isEqualTo("햇반 24개 공구");
            assertThat(n.get("group_buy_id")).isEqualTo(id);
            assertThat(n.get("read_at")).isNull();
        });
    }

    @Test
    void closingAnExpiredUnderfilledGroupBuyNotifiesEveryoneWithAFailureNotice() {
        long id = groupBuyExpiredAt(START.plus(Duration.ofHours(1)));
        clock.set(START.plus(Duration.ofHours(1)).plusSeconds(2));

        closeService.closeExpired();

        assertThat(recipientsOf("GROUPBUY_FAILED")).containsExactly(ORGANIZER, PARTICIPANT, OUTSIDER);
        assertThat(notificationCount()).isEqualTo(3);
    }

    private long groupBuyExpiredAt(java.time.Instant deadline) {
        long id = com.camgong.support.GroupBuySeed.groupBuy().title("실패 예정").deadline(deadline).quantities(24, 14).insert(jdbc);
        participate(id, ORGANIZER, 9);
        participate(id, PARTICIPANT, 3);
        participate(id, OUTSIDER, 2);
        return id;
    }

    @Test
    void anEditNotifiesTheParticipantsButNotTheOrganizerAndListsWhatChanged() throws Exception {
        long id = openWithParticipants();
        var body = GroupBuyBodies.with(GroupBuyBodies.valid(), "unitPrice", 1500);
        body = GroupBuyBodies.with(body, "deadline", GroupBuyBodies.iso(START.plus(Duration.ofDays(8))));
        updateAs(ORGANIZER, id, body).andExpect(status().isOk());

        assertThat(recipientsOf("GROUPBUY_CHANGED")).containsExactly(PARTICIPANT, OUTSIDER);
        assertThat(notifications()).allSatisfy(n -> assertThat((String) n.get("changed_fields")).isEqualTo("unitPrice,deadline"));
    }

    @Test
    void cancellingNotifiesTheParticipantsAndTheTitleSurvivesTheCancellation() throws Exception {
        long id = openWithParticipants();
        cancelAs(ORGANIZER, id).andExpect(status().isNoContent());

        assertThat(recipientsOf("GROUPBUY_CANCELLED")).containsExactly(PARTICIPANT, OUTSIDER);
        assertThat(notifications()).allSatisfy(n -> assertThat(n.get("group_buy_title")).isEqualTo("햇반 24개 공구"));
    }

    @Test
    void changingOnlyTheOpenChatLinkCreatesNoNotification() throws Exception {
        long id = openWithParticipants();
        var body = GroupBuyBodies.with(GroupBuyBodies.valid(), "openChatUrl", "https://open.kakao.com/o/changed");
        updateAs(ORGANIZER, id, body).andExpect(status().isOk());

        assertThat(notificationCount()).isZero();
    }

    @Test
    void aGroupBuyWithOnlyTheOrganizerNotifiesNobodyOnEditOrCancel() throws Exception {
        long id = openGroupBuy();                                                  // 개설자만 있다
        var body = GroupBuyBodies.with(GroupBuyBodies.valid(), "unitPrice", 1500);
        updateAs(ORGANIZER, id, body).andExpect(status().isOk());
        cancelAs(ORGANIZER, id).andExpect(status().isNoContent());

        assertThat(notificationCount()).isZero();
    }

    @Test
    void aRolledBackEditCreatesNoNotification() throws Exception {
        long id = openWithParticipants();
        var invalid = GroupBuyBodies.with(GroupBuyBodies.valid(), "totalQuantity", 5);
        updateAs(ORGANIZER, id, invalid).andExpect(status().isBadRequest());

        assertThat(notificationCount()).isZero();
    }

    @Test
    void joiningChangingQuantityAndLeavingCreateNoNotification() throws Exception {
        long id = openWithParticipants();
        seedMembers(4, 4);
        mockMvc.perform(post("/api/groupbuys/" + id + "/participations").with(as(4)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2,\"expectedUnitPrice\":1200}")).andExpect(status().isCreated());
        mockMvc.perform(put("/api/groupbuys/" + id + "/participations/me").with(as(4)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":3,\"expectedUnitPrice\":1200}")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/groupbuys/" + id + "/participations/me").with(as(4))).andExpect(status().isNoContent());

        assertThat(notificationCount()).isZero();
    }

    @Test
    void notificationsCarryNoOtherMembersData() throws Exception {
        long id = openWithParticipants();
        cancelAs(ORGANIZER, id).andExpect(status().isNoContent());

        String dump = notifications().toString();
        assertThat(dump).doesNotContain("참여자").doesNotContain("외부인").doesNotContain("@school.ac.kr").doesNotContain("open.kakao");
        // 컬럼 구성: 제목·종류·바뀐 항목 이름·시각뿐
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = 'notification'", String.class))
                .containsExactlyInAnyOrder("id", "event_id", "recipient_id", "type", "group_buy_id", "group_buy_title",
                        "changed_fields", "created_at", "read_at");
    }

    @Test
    void anEditWithAnUnchangedBodyCreatesNoNotification() throws Exception {
        long id = openWithParticipants();
        updateAs(ORGANIZER, id, GroupBuyBodies.valid()).andExpect(status().isOk());      // 개설 때와 같은 내용

        assertThat(notificationCount()).isZero();
    }
}

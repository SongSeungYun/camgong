package com.camgong.groupbuy.query;

import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 12, 13, 14, 15, 16, 17, 21(일부). 001 의 LinkVisibilityIT 를 흡수했다. */
class GroupBuyDetailIT extends IntegrationTestBase {

    private static final String URL = "https://open.kakao.com/o/abc";

    private ResultActions detailAs(long viewer, long id) throws Exception {
        return mockMvc.perform(get("/api/groupbuys/" + id).with(as(viewer)));
    }

    /** 개설자 참여 행까지 있는 공구(개설 API 와 같은 모양). 참여 행의 수량 합이 secured 와 맞도록 개설자 수량을 쓴다. */
    private long seedOrganized(String status, Instant deadline, int organizerQty) {
        long id = groupBuy().status(status).deadline(deadline).quantities(24, organizerQty).price(1200).insert(jdbc);
        jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, 1, ?, ?)",
                id, organizerQty, java.time.LocalDateTime.ofInstant(START, java.time.ZoneOffset.UTC));
        return id;
    }

    @Test
    void generalMemberSeesPublicFieldsOnly() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 6);

        detailAs(OUTSIDER, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("햇반 24개 공구"))
                .andExpect(jsonPath("$.description").value("같이 사요"))
                .andExpect(jsonPath("$.itemName").value("햇반 210g"))
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.itemUrl").value("https://example.com/item/123"))
                .andExpect(jsonPath("$.unitPrice").value(1200))
                .andExpect(jsonPath("$.totalQuantity").value(24))
                .andExpect(jsonPath("$.securedQuantity").value(15))
                .andExpect(jsonPath("$.remainingQuantity").value(9))
                .andExpect(jsonPath("$.deadline").value("2026-11-03T09:00:00+09:00"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.organizerName").value("개설자"))
                .andExpect(jsonPath("$.isOrganizer").value(false))
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.myParticipation").value(nullValue()))
                .andExpect(jsonPath("$.openChatUrl").value(nullValue()))
                .andExpect(jsonPath("$.participants").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").value("2026-10-27T09:00:00+09:00"))
                .andExpect(jsonPath("$.organizerId").doesNotExist());
    }

    @Test
    void participantSeesOwnQuantityAndAmountButNotOtherParticipants() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 6);

        detailAs(PARTICIPANT, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.isOrganizer").value(false))
                .andExpect(jsonPath("$.myParticipation.quantity").value(6))
                .andExpect(jsonPath("$.myParticipation.amount").value(7200))
                .andExpect(jsonPath("$.myParticipation.paidConfirmedAt").value(nullValue()))
                .andExpect(jsonPath("$.myParticipation.receivedConfirmedAt").value(nullValue()))
                .andExpect(jsonPath("$.participants").value(nullValue()));
    }

    @Test
    void organizerSeesRosterAndHisOwnQuantityWithoutConfirmationFields() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 6);

        detailAs(ORGANIZER, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.isOrganizer").value(true))
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.myParticipation.quantity").value(9))      // 프론트 수정 폼의 '내 수량'
                .andExpect(jsonPath("$.myParticipation.amount").value(10800))
                .andExpect(jsonPath("$.myParticipation.paidConfirmedAt").doesNotExist())
                .andExpect(jsonPath("$.myParticipation.receivedConfirmedAt").doesNotExist())
                .andExpect(jsonPath("$.participants", hasSize(2)))
                .andExpect(jsonPath("$.participants[0].memberId").value(1))
                .andExpect(jsonPath("$.participants[0].name").value("개설자"))
                .andExpect(jsonPath("$.participants[0].quantity").value(9))
                .andExpect(jsonPath("$.participants[1].memberId").value(2))
                .andExpect(jsonPath("$.participants[1].name").value("참여자"))
                .andExpect(jsonPath("$.participants[1].quantity").value(6));
    }

    @Test
    void openChatLinkFollowsTheVisibilityTable() throws Exception {
        Instant open = START.plus(Duration.ofDays(1));
        Instant passed = START.minusSeconds(1);
        record Case(String status, Instant deadline, boolean organizer, boolean participant) {
        }
        for (Case c : new Case[] {
                new Case("OPEN", open, true, false),
                new Case("OPEN", passed, true, false),          // PENDING_CLOSE
                new Case("COMPLETED", open, true, true),
                new Case("FAILED", passed, false, false) }) {
            long id = seedOrganized(c.status(), c.deadline(), 9);
            seedParticipantRowOnly(id, PARTICIPANT, 3);
            String label = c.status() + "/" + c.deadline();
            expectLink(id, ORGANIZER, c.organizer(), label);
            expectLink(id, PARTICIPANT, c.participant(), label);
            expectLink(id, OUTSIDER, false, label);
        }
    }

    private void seedParticipantRowOnly(long id, long memberId, int qty) {
        jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, ?, ?, ?)",
                id, memberId, qty, java.time.LocalDateTime.ofInstant(START, java.time.ZoneOffset.UTC));
    }

    private void expectLink(long id, long viewer, boolean visible, String label) throws Exception {
        ResultActions r = detailAs(viewer, id).andExpect(status().isOk());
        if (visible) {
            r.andExpect(jsonPath("$.openChatUrl").value(URL));
        } else {
            r.andExpect(jsonPath("$.openChatUrl").value(nullValue()));
        }
    }

    @Test
    void displayStatusInDetailIsDerivedLikeTheList() throws Exception {
        long pending = seedOrganized("OPEN", START.minusSeconds(1), 9);
        long failed = seedOrganized("FAILED", START.minusSeconds(1), 9);
        detailAs(OUTSIDER, pending).andExpect(jsonPath("$.status").value("PENDING_CLOSE"));
        detailAs(OUTSIDER, failed).andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void withoutLoginIs401EvenForAnExistingGroupBuy() throws Exception {
        long id = openGroupBuy();
        mockMvc.perform(get("/api/groupbuys/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groupbuys/" + id).header("X-Member-Id", "1")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrCancelledGroupBuyIs404() throws Exception {
        long id = openGroupBuy();
        cancelAs(ORGANIZER, id).andExpect(status().isNoContent());
        detailAs(OUTSIDER, id).andExpect(status().isNotFound());
        detailAs(OUTSIDER, 99999).andExpect(status().isNotFound());
    }

    @Test
    void listAndDetailAgreeOnQuantitiesAndStatus() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 6);
        String list = mockMvc.perform(get("/api/groupbuys")).andReturn().getResponse().getContentAsString();
        String detail = detailAs(OUTSIDER, id).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat((Integer) com.jayway.jsonpath.JsonPath.read(list, "$.items[0].securedQuantity"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.<Integer>read(detail, "$.securedQuantity")).isEqualTo(15);
        org.assertj.core.api.Assertions.assertThat((String) com.jayway.jsonpath.JsonPath.read(list, "$.items[0].status"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.<String>read(detail, "$.status"));
    }
}

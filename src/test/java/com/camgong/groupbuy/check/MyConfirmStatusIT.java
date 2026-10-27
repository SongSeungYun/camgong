package com.camgong.groupbuy.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** quickstart 시나리오 10: 참여자는 자기 입금·수령 확인 상태만 본다 */
class MyConfirmStatusIT extends CheckTestBase {

    @Test
    void eachParticipantSeesOnlyHisOwnConfirmationState() throws Exception {
        long id = completedGroupBuy();
        clock.advance(java.time.Duration.ofHours(2));                  // 공구 생성 시각(09:00)과 겹치지 않는 확인 시각
        pay(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk());

        String mine = mockMvc.perform(get("/api/groupbuys/" + id).with(as(PARTICIPANT))).andExpect(status().isOk())
                .andExpect(jsonPath("$.myParticipation.quantity").value(6))
                .andExpect(jsonPath("$.myParticipation.paidConfirmedAt").value("2026-10-27T11:00:00+09:00"))
                .andExpect(jsonPath("$.myParticipation.receivedConfirmedAt").value((Object) null))
                .andExpect(jsonPath("$.participants").value((Object) null)).andReturn().getResponse().getContentAsString();

        String other = mockMvc.perform(get("/api/groupbuys/" + id).with(as(OUTSIDER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.myParticipation.quantity").value(9))
                .andExpect(jsonPath("$.myParticipation.paidConfirmedAt").value((Object) null))
                .andExpect(jsonPath("$.myParticipation.receivedConfirmedAt").value((Object) null)).andReturn().getResponse().getContentAsString();

        // 서로의 상태는 응답 어디에도 없다: 다른 참여자의 이름도 시각도 나오지 않는다
        assertThat(other).doesNotContain("2026-10-27T11:00:00").doesNotContain("참여자");
        assertThat(mine).doesNotContain("외부인");
    }

    @Test
    void theOrganizersOwnParticipationHasNoConfirmationFields() throws Exception {
        long id = completedGroupBuy();
        pay(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/groupbuys/" + id).with(as(ORGANIZER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.myParticipation.quantity").value(9))
                .andExpect(jsonPath("$.myParticipation.paidConfirmedAt").doesNotExist())
                .andExpect(jsonPath("$.myParticipation.receivedConfirmedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("paidConfirmedAt\":null");
    }

    @Test
    void revertingTheCheckClearsItInTheParticipantsView() throws Exception {
        long id = completedGroupBuy();
        pay(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk());
        receive(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk());
        mockMvc.perform(get("/api/groupbuys/" + id).with(as(PARTICIPANT)))
                .andExpect(jsonPath("$.myParticipation.receivedConfirmedAt").value("2026-10-27T09:00:00+09:00"));

        pay(ORGANIZER, id, PARTICIPANT, FALSE).andExpect(status().isOk());
        receive(ORGANIZER, id, PARTICIPANT, FALSE).andExpect(status().isOk());

        mockMvc.perform(get("/api/groupbuys/" + id).with(as(PARTICIPANT)))
                .andExpect(jsonPath("$.myParticipation.paidConfirmedAt").value((Object) null))
                .andExpect(jsonPath("$.myParticipation.receivedConfirmedAt").value((Object) null));
    }
}

package com.camgong.groupbuy.check;

import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** quickstart 시나리오 4, 5, 12, 13, 15 */
class CheckAccessIT extends CheckTestBase {

    @Test
    void onlyTheOrganizerMayOpenOrChangeTheSheet() throws Exception {
        long id = completedGroupBuy();
        for (long other : new long[] { PARTICIPANT, OUTSIDER }) {
            sheet(other, id, "").andExpect(status().isForbidden()).andExpect(jsonPath("$.title").value("ORGANIZER_ONLY"));
            pay(other, id, PARTICIPANT, TRUE).andExpect(status().isForbidden()).andExpect(jsonPath("$.title").value("ORGANIZER_ONLY"));
            receive(other, id, PARTICIPANT, TRUE).andExpect(status().isForbidden());
        }
        org.assertj.core.api.Assertions.assertThat(paidAt(id, PARTICIPANT)).isNull();
    }

    @Test
    void groupBuysThatAreNotCompletedAreNotCheckable() throws Exception {
        long open = groupBuy().status("OPEN").insert(jdbc);
        long pending = groupBuy().status("OPEN").deadline(START.minusSeconds(60)).insert(jdbc);   // 마감됨(확정 대기)
        long failed = groupBuy().status("FAILED").insert(jdbc);
        for (long id : new long[] { open, pending, failed }) {
            participate(id, ORGANIZER, 9);
            sheet(ORGANIZER, id, "").andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("NOT_CHECKABLE"));
            pay(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("NOT_CHECKABLE"));
        }
        long cancelled = groupBuy().status("CANCELLED").insert(jdbc);
        sheet(ORGANIZER, cancelled, "").andExpect(status().isNotFound());
        pay(ORGANIZER, cancelled, PARTICIPANT, TRUE).andExpect(status().isNotFound());
        sheet(ORGANIZER, 99999, "").andExpect(status().isNotFound());
    }

    @Test
    void aNonOrganizerCannotLearnTheStatusFromTheError() throws Exception {
        long open = groupBuy().status("OPEN").insert(jdbc);
        sheet(PARTICIPANT, open, "").andExpect(status().isForbidden());                // 409 가 아니라 403
        pay(OUTSIDER, open, PARTICIPANT, TRUE).andExpect(status().isForbidden());
    }

    @Test
    void theOrganizerHimselfAndNonParticipantsAreInvalidTargets() throws Exception {
        long id = completedGroupBuy();
        pay(ORGANIZER, id, ORGANIZER, TRUE).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_TARGET")).andExpect(jsonPath("$.errors[*].field").value(hasItem("memberId")));
        receive(ORGANIZER, id, ORGANIZER, TRUE).andExpect(status().isBadRequest());
        seedMembers(4, 4);
        pay(ORGANIZER, id, 4, TRUE).andExpect(status().isNotFound())                   // 이 공구에 참여하지 않은 회원
                .andExpect(jsonPath("$.title").value("PARTICIPATION_NOT_FOUND"));
        pay(ORGANIZER, id, 99999, TRUE).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("PARTICIPATION_NOT_FOUND"));
        org.assertj.core.api.Assertions.assertThat(paidAt(id, ORGANIZER)).isNull();
    }

    @Test
    void aBodyWithoutConfirmedOrOfTheWrongTypeIs400() throws Exception {
        long id = completedGroupBuy();
        pay(ORGANIZER, id, PARTICIPANT, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("confirmed")));
        pay(ORGANIZER, id, PARTICIPANT, "{\"confirmed\":\"yes\"}").andExpect(status().isBadRequest());
        receive(ORGANIZER, id, PARTICIPANT, "{\"confirmed\":null}").andExpect(status().isBadRequest());
    }

    @Test
    void withoutLoginIs401() throws Exception {
        long id = completedGroupBuy();
        mockMvc.perform(get("/api/groupbuys/" + id + "/check-sheet")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/groupbuys/" + id + "/participations/" + PARTICIPANT + "/payment")
                .contentType(MediaType.APPLICATION_JSON).content(TRUE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groupbuys/" + id + "/check-sheet").header("X-Member-Id", "1")).andExpect(status().isUnauthorized());
    }

    @Test
    void aMemberWhoParticipatesInAnotherGroupBuyIsNotATargetHere() throws Exception {
        long id = completedGroupBuy();
        long other = completedGroupBuy();
        seedMembers(4, 4);
        participate(other, 4, 1);                                                      // 4 는 다른 공구에만 참여한다

        pay(ORGANIZER, id, 4, TRUE).andExpect(status().isNotFound()).andExpect(jsonPath("$.title").value("PARTICIPATION_NOT_FOUND"));
        receive(ORGANIZER, id, 4, TRUE).andExpect(status().isNotFound()).andExpect(jsonPath("$.title").value("PARTICIPATION_NOT_FOUND"));
        org.assertj.core.api.Assertions.assertThat(paidAt(other, 4)).isNull();         // 다른 공구의 행은 건드리지 않았다
    }

    @Test
    void anInvalidBodyIs400BeforeAnyAccessCheck() throws Exception {
        long id = completedGroupBuy();
        long open = groupBuy().status("OPEN").insert(jdbc);
        pay(OUTSIDER, id, PARTICIPANT, "{}").andExpect(status().isBadRequest());       // 개설자가 아니어도 403 이 아니라 400
        pay(ORGANIZER, open, PARTICIPANT, "{}").andExpect(status().isBadRequest());    // 모집 완료가 아니어도 409 가 아니라 400
        pay(ORGANIZER, 99999, PARTICIPANT, "{}").andExpect(status().isBadRequest());   // 없는 공구여도 404 가 아니라 400
    }
}

package com.camgong.groupbuy.check;

import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** quickstart 시나리오 1, 11, 14, 15, 17 */
class CheckSheetIT extends CheckTestBase {

    @Test
    void organizerSeesTheParticipantsWithoutHimselfAndASummary() throws Exception {
        long id = completedGroupBuy();

        sheet(ORGANIZER, id, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.participantCount").value(2))
                .andExpect(jsonPath("$.summary.paidCount").value(0))
                .andExpect(jsonPath("$.summary.receivedCount").value(0))
                .andExpect(jsonPath("$.summary.amountDue").value(18000))
                .andExpect(jsonPath("$.summary.amountConfirmed").value(0))
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].memberId").value(PARTICIPANT))
                .andExpect(jsonPath("$.rows[0].name").value("참여자"))
                .andExpect(jsonPath("$.rows[0].quantity").value(6))
                .andExpect(jsonPath("$.rows[0].amount").value(7200))
                .andExpect(jsonPath("$.rows[0].paidConfirmedAt").value((Object) null))
                .andExpect(jsonPath("$.rows[0].receivedConfirmedAt").value((Object) null))
                .andExpect(jsonPath("$.rows[0].receivedWithoutPayment").value(false))
                .andExpect(jsonPath("$.rows[1].memberId").value(OUTSIDER))
                .andExpect(jsonPath("$.rows[1].amount").value(10800))
                .andExpect(jsonPath("$.rows[*].memberId", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem((int) ORGANIZER))));
    }

    @Test
    void noParticipantsMeansAnEmptyListNotAnError() throws Exception {
        long id = groupBuy().status("COMPLETED").quantities(24, 24).insert(jdbc);
        participate(id, ORGANIZER, 24);

        sheet(ORGANIZER, id, "").andExpect(status().isOk()).andExpect(jsonPath("$.rows", empty()))
                .andExpect(jsonPath("$.summary.participantCount").value(0))
                .andExpect(jsonPath("$.summary.amountDue").value(0))
                .andExpect(jsonPath("$.summary.amountConfirmed").value(0));
    }

    @Test
    void filtersNarrowTheRowsButNeverTheSummary() throws Exception {
        long id = completedGroupBuy();
        pay(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk());
        receive(ORGANIZER, id, OUTSIDER, TRUE).andExpect(status().isOk());

        sheet(ORGANIZER, id, "?filter=unpaid").andExpect(jsonPath("$.rows[*].memberId", contains((int) OUTSIDER)))
                .andExpect(jsonPath("$.summary.participantCount").value(2)).andExpect(jsonPath("$.summary.paidCount").value(1));
        sheet(ORGANIZER, id, "?filter=unreceived").andExpect(jsonPath("$.rows[*].memberId", contains((int) PARTICIPANT)))
                .andExpect(jsonPath("$.summary.receivedCount").value(1));
        sheet(ORGANIZER, id, "?filter=all").andExpect(jsonPath("$.rows.length()").value(2));
        sheet(ORGANIZER, id, "?filter=x").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("filter"));
    }

    @Test
    void receivedWithoutPaymentIsFlaggedAndReopeningShowsTheSameState() throws Exception {
        long id = completedGroupBuy();
        receive(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk());

        String first = sheet(ORGANIZER, id, "").andExpect(jsonPath("$.rows[0].receivedWithoutPayment").value(true))
                .andReturn().getResponse().getContentAsString();
        String second = sheet(ORGANIZER, id, "").andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);
    }
}

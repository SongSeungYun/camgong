package com.camgong.groupbuy.join;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.camgong.support.Concurrent;

/** quickstart 시나리오 12, 13, 14(취소 쪽) */
class LeaveParticipationIT extends JoinTestBase {

    @BeforeEach
    void members() {
        seedMembers(4, 10);
    }

    @Test
    void leavingFreesTheQuantityKeepsTheGroupBuyOpenAndAllowsRejoining() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());
        assertThat(secured(id)).isEqualTo(15);

        leaveAs(2, id).andExpect(status().isNoContent());

        assertThat(secured(id)).isEqualTo(9);
        assertThat(statusOf(id)).isEqualTo("OPEN");
        assertThat(participationRows(id)).isEqualTo(1);
        assertThat(i1Violations()).isEmpty();
        assertThat(events.completed()).isEmpty();                                // 취소는 이벤트가 없다
        joinAs(2, id, 6).andExpect(status().isCreated());                         // 재참여 가능
    }

    @Test
    void organizerCannotLeaveHisOwnGroupBuy() throws Exception {
        long id = openGroupBuy();
        leaveAs(ORGANIZER, id).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("ORGANIZER_NOT_ALLOWED"));
        assertThat(secured(id)).isEqualTo(9);
    }

    @Test
    void leavingWithoutAParticipationIs404() throws Exception {
        long id = openGroupBuy();
        leaveAs(2, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.title").value("PARTICIPATION_NOT_FOUND"));
        leaveAs(2, 99999).andExpect(status().isNotFound());
    }

    @Test
    void leavingIsRejectedOnceCompletedOrPastTheDeadline() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 15).andExpect(status().isCreated());                         // 가득 참 → COMPLETED
        leaveAs(2, id).andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("NOT_JOINABLE"))
                .andExpect(jsonPath("$.reason").value("COMPLETED"));
        assertThat(secured(id)).isEqualTo(24);

        long late = openGroupBuy(com.camgong.support.GroupBuyBodies.with(com.camgong.support.GroupBuyBodies.valid(),
                "title", "마감 지남"));
        joinAs(3, late, 2).andExpect(status().isCreated());
        clock.advance(Duration.ofDays(7));
        leaveAs(3, late).andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("DEADLINE_PASSED"));
        assertThat(secured(late)).isEqualTo(11);
        assertThat(i1Violations()).isEmpty();
    }

    @Test
    void withoutLoginIs401() throws Exception {
        long id = openGroupBuy();
        mockMvc.perform(delete("/api/groupbuys/" + id + "/participations/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void leavingARemovedGroupBuyIs404() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 3).andExpect(status().isCreated());
        cancelAs(ORGANIZER, id).andExpect(status().isNoContent());
        leaveAs(2, id).andExpect(status().isNotFound());
    }

    @Test
    void sameMemberLeavingTwiceAtOnceFreesTheQuantityOnlyOnce() throws Exception {
        for (int round = 0; round < 10; round++) {
            long id = openGroupBuy(com.camgong.support.GroupBuyBodies.with(com.camgong.support.GroupBuyBodies.valid(),
                    "title", "이중 취소 " + round));
            joinAs(2, id, 6).andExpect(status().isCreated());
            joinAs(3, id, 4).andExpect(status().isCreated());

            List<Integer> statuses = Concurrent.all(List.of(
                    () -> leaveAs(2, id).andReturn().getResponse().getStatus(),
                    () -> leaveAs(2, id).andReturn().getResponse().getStatus()));

            assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(204, 404);
            assertThat(secured(id)).as("한 번만 차감").isEqualTo(13);            // 9 + 4
            assertThat(i1Violations()).isEmpty();
        }
    }
}

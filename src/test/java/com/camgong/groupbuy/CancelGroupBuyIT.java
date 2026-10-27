package com.camgong.groupbuy;

import static com.camgong.support.GroupBuyBodies.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 16, 17 */
class CancelGroupBuyIT extends IntegrationTestBase {

    @Test
    void cancelHidesTheGroupBuyButKeepsTheRow() throws Exception {
        long id = openGroupBuy();

        cancelAs(ORGANIZER, id).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/groupbuys/" + id).with(as(ORGANIZER))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT status FROM group_buy WHERE id = ?", String.class, id))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participation WHERE group_buy_id = ?", Integer.class, id))
                .isEqualTo(1); // 행을 지우지 않는다
    }

    @Test
    void nothingCanBeModifiedAfterTheDeadline() throws Exception {
        long id = openGroupBuy(); // 마감 START+7일
        clock.set(START.plus(Duration.ofDays(8)));

        updateAs(ORGANIZER, id, valid(START.plus(Duration.ofDays(9))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("NOT_MODIFIABLE"))
                .andExpect(jsonPath("$.reason").value("DEADLINE_PASSED"));
        cancelAs(ORGANIZER, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("NOT_MODIFIABLE"))
                .andExpect(jsonPath("$.reason").value("DEADLINE_PASSED"));
    }

    @Test
    void onlyOrganizerMayCancel() throws Exception {
        long id = openGroupBuy();

        cancelAs(OUTSIDER, id).andExpect(status().isForbidden());
        cancelAs(99, id).andExpect(status().isUnauthorized());
    }

    @Test
    void cancellingTwiceOrUpdatingACancelledOneIsNotFound() throws Exception {
        long id = openGroupBuy();
        cancelAs(ORGANIZER, id).andExpect(status().isNoContent());

        cancelAs(ORGANIZER, id).andExpect(status().isNotFound());
        updateAs(ORGANIZER, id, valid()).andExpect(status().isNotFound());
    }

    @Test
    void completedGroupBuyCannotBeCancelled() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 15); // 24/24
        jdbc.update("UPDATE group_buy SET status = 'COMPLETED' WHERE id = ?", id);

        cancelAs(ORGANIZER, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("NOT_MODIFIABLE"))
                .andExpect(jsonPath("$.reason").value("COMPLETED"));
    }
}

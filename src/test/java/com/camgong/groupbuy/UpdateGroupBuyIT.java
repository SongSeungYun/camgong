package com.camgong.groupbuy;

import static com.camgong.support.GroupBuyBodies.valid;
import static com.camgong.support.GroupBuyBodies.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 9~15 */
class UpdateGroupBuyIT extends IntegrationTestBase {

    private int securedColumn(long id) {
        return jdbc.queryForObject("SELECT secured_quantity FROM group_buy WHERE id = ?", Integer.class, id);
    }

    private int quantityOf(long id, long memberId) {
        return jdbc.queryForObject("SELECT quantity FROM participation WHERE group_buy_id = ? AND member_id = ?",
                Integer.class, id, memberId);
    }

    @Test
    void updatesPriceAndOrganizerQuantityTogetherWithSecured() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 3); // secured 12

        updateAs(ORGANIZER, id, with(with(valid(), "unitPrice", 1500), "organizerQuantity", 12))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPrice").value(1500))
                .andExpect(jsonPath("$.organizerQuantity").value(12))
                .andExpect(jsonPath("$.securedQuantity").value(15))
                .andExpect(jsonPath("$.remainingQuantity").value(9))
                .andExpect(jsonPath("$.status").value("OPEN"));

        assertThat(quantityOf(id, ORGANIZER)).isEqualTo(12);
        assertThat(securedColumn(id)).isEqualTo(15);
    }

    @Test
    void onlyOrganizerMayUpdate() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 3);

        updateAs(OUTSIDER, id, valid()).andExpect(status().isForbidden());
        updateAs(PARTICIPANT, id, valid()).andExpect(status().isForbidden());
    }

    @Test
    void unknownGroupBuyAndUnknownMember() throws Exception {
        updateAs(ORGANIZER, 9999, valid()).andExpect(status().isNotFound());
        updateAs(99, 1, valid()).andExpect(status().isUnauthorized());
    }

    @Test
    void deadlineCannotBeShortened() throws Exception {
        long id = openGroupBuy(); // 마감 START+7일

        updateAs(ORGANIZER, id, valid(START.plus(Duration.ofDays(6))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='deadline')]").exists());
    }

    @Test
    void deadlineExtensionIsCappedAtThirtyDaysFromCreation() throws Exception {
        long id = openGroupBuy();

        updateAs(ORGANIZER, id, valid(START.plus(Duration.ofDays(30)).plusSeconds(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='deadline')]").exists());
        updateAs(ORGANIZER, id, valid(START.plus(Duration.ofDays(30))))
                .andExpect(status().isOk())
                .andReturn();
        String deadline = jdbc.queryForObject("SELECT DATE_FORMAT(deadline, '%Y-%m-%d %H:%i:%s') FROM group_buy WHERE id = ?",
                String.class, id);
        assertThat(deadline).isEqualTo("2026-11-26 00:00:00"); // START + 30일(UTC)
    }

    @Test
    void totalQuantityCannotDropBelowSecured() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 5); // secured 14

        updateAs(ORGANIZER, id, with(valid(), "totalQuantity", 13))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='totalQuantity')]").exists());
        assertThat(securedColumn(id)).isEqualTo(14);
    }

    @Test
    void organizerIncreaseBeyondTotalIsRejected() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 6); // secured 15, 개설자가 19 로 올리면 25 > 24

        updateAs(ORGANIZER, id, with(valid(), "organizerQuantity", 19))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='organizerQuantity')]").exists());
        assertThat(securedColumn(id)).isEqualTo(15);
        assertThat(quantityOf(id, ORGANIZER)).isEqualTo(9);
    }

    @Test
    void organizerQuantityMustStayPositive() throws Exception {
        long id = openGroupBuy();

        updateAs(ORGANIZER, id, with(valid(), "organizerQuantity", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='organizerQuantity')]").exists());
    }

    @Test
    void exactFillCompletesAndThenNothingCanBeModified() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 6); // secured 15

        updateAs(ORGANIZER, id, with(valid(), "organizerQuantity", 18)) // 6 + 18 = 24
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.securedQuantity").value(24))
                .andExpect(jsonPath("$.remainingQuantity").value(0));
        assertThat(jdbc.queryForObject("SELECT status FROM group_buy WHERE id = ?", String.class, id))
                .isEqualTo("COMPLETED");

        updateAs(ORGANIZER, id, valid())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("NOT_MODIFIABLE"))
                .andExpect(jsonPath("$.reason").value("COMPLETED"));
        cancelAs(ORGANIZER, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("NOT_MODIFIABLE"));
    }
}

package com.camgong.groupbuy.join;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.camgong.support.Concurrent;

/** quickstart 시나리오 9, 10, 11, 13(변경 쪽) */
class ChangeParticipationIT extends JoinTestBase {

    @BeforeEach
    void members() {
        seedMembers(4, 10);
    }

    @Test
    void loweringTheQuantityFreesTheDifference() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());

        changeAs(2, id, 3).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.amount").value(3600))
                .andExpect(jsonPath("$.securedQuantity").value(12))
                .andExpect(jsonPath("$.remainingQuantity").value(12))
                .andExpect(jsonPath("$.status").value("OPEN"));

        assertThat(secured(id)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT quantity FROM participation WHERE group_buy_id = ? AND member_id = 2",
                Integer.class, id)).isEqualTo(3);
        assertThat(i1Violations()).isEmpty();
        assertThat(events.completed()).isEmpty();
    }

    @Test
    void raisingBeyondWhatIsLeftIsRejectedWithTheRemainingNumber() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());                     // 남은 9

        changeAs(2, id, 16).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("INSUFFICIENT_QUANTITY"))
                .andExpect(jsonPath("$.remainingQuantity").value(9));            // 내 수량을 뺀 공구 전체의 남은 수량
        assertThat(secured(id)).isEqualTo(15);
        changeAs(2, id, 15).andExpect(status().isOk());                       // 한도: 남은 9 + 내 6
    }

    @Test
    void raisingToExactlyFullCompletesOnceAndNotifiesEveryone() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());
        joinAs(3, id, 3).andExpect(status().isCreated());                     // 남은 6

        changeAs(2, id, 12).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(statusOf(id)).isEqualTo("COMPLETED");
        assertThat(events.completed()).hasSize(1);
        assertThat(events.completed().get(0).recipientIds()).containsExactlyInAnyOrder(ORGANIZER, 2L, 3L);
        changeAs(2, id, 11).andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("COMPLETED"));
    }

    @Test
    void sameQuantityIsA200WithNoChange() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());

        changeAs(2, id, 6).andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(6))
                .andExpect(jsonPath("$.securedQuantity").value(15));
        assertThat(secured(id)).isEqualTo(15);
        assertThat(i1Violations()).isEmpty();
    }

    @Test
    void organizerAndNonParticipantsAreRefused() throws Exception {
        long id = openGroupBuy();
        changeAs(ORGANIZER, id, 5).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("ORGANIZER_NOT_ALLOWED"));
        changeAs(2, id, 5).andExpect(status().isNotFound()).andExpect(jsonPath("$.title").value("PARTICIPATION_NOT_FOUND"));
        changeAs(2, 99999, 5).andExpect(status().isNotFound());
    }

    @Test
    void staleUnitPriceIsRejectedOnChange() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());
        jdbc.update("UPDATE group_buy SET unit_price = 1500 WHERE id = ?", id);

        changeAs(2, id, 4, 1200).andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.currentUnitPrice").value(1500));
        changeAs(2, id, 4, 1500).andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(6000));
    }

    @Test
    void invalidQuantityIs400() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());
        changeAs(2, id, 0).andExpect(status().isBadRequest());
        assertThat(secured(id)).isEqualTo(15);
    }

    @Test
    void twoChangesOfTheSameMemberAtOnceNeverDoubleCount() throws Exception {
        for (int round = 0; round < 10; round++) {
            long id = openGroupBuy(com.camgong.support.GroupBuyBodies.with(com.camgong.support.GroupBuyBodies.valid(),
                    "title", "이중 변경 " + round));
            joinAs(2, id, 6).andExpect(status().isCreated());

            List<Integer> statuses = Concurrent.all(List.of(
                    () -> changeAs(2, id, 3).andReturn().getResponse().getStatus(),
                    () -> changeAs(2, id, 3).andReturn().getResponse().getStatus()));

            assertThat(statuses).as("round %d", round).containsOnly(200);
            assertThat(secured(id)).isEqualTo(12);
            assertThat(i1Violations()).isEmpty();
        }
    }
}

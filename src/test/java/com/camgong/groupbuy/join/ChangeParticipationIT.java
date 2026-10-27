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

            Overlap.arm(2);
            List<Integer> statuses;
            try {
                statuses = Concurrent.all(List.of(
                        () -> changeAs(2, id, 3).andReturn().getResponse().getStatus(),
                        () -> changeAs(2, id, 3).andReturn().getResponse().getStatus()));
            } finally {
                Overlap.disarm();
            }

            assertThat(statuses).as("round %d", round).containsOnly(200);
            assertThat(secured(id)).isEqualTo(12);
            assertThat(i1Violations()).isEmpty();
        }
    }

    @Test
    void hugeQuantitiesAreRejectedAsInsufficientNotAsOverflow() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());                         // 확보 15, 내 수량 6, 남은 9
        for (int q : new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE - 8, Integer.MAX_VALUE - 14, 16 }) {
            changeAs(2, id, q).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("INSUFFICIENT_QUANTITY"))
                    .andExpect(jsonPath("$.remainingQuantity").value(9));
        }
        assertThat(secured(id)).isEqualTo(15);
        assertThat(i1Violations()).isEmpty();
    }

    @Test
    void theSameQuantityIsRetrySafeAfterCompletionAndAfterTheDeadline() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());
        changeAs(2, id, 15).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        // 응답을 못 받고 같은 본문을 다시 보냈다: 이미 COMPLETED 여도 200, 이벤트는 다시 나가지 않는다
        changeAs(2, id, 15).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.quantity").value(15));
        assertThat(events.completed()).hasSize(1);
        changeAs(2, id, 14).andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("COMPLETED"));   // 다른 수량은 여전히 거부

        long late = openGroupBuy(com.camgong.support.GroupBuyBodies.with(com.camgong.support.GroupBuyBodies.valid(), "title", "마감 지남"));
        joinAs(3, late, 2).andExpect(status().isCreated());
        clock.advance(java.time.Duration.ofDays(7));
        changeAs(3, late, 2).andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(2));
        changeAs(3, late, 3).andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("DEADLINE_PASSED"));
    }

    @Test
    void theSameQuantityOnAFailedGroupBuyIsRefusedAndOnACancelledOneIs404() throws Exception {
        long failed = openGroupBuy();
        joinAs(2, failed, 6).andExpect(status().isCreated());
        jdbc.update("UPDATE group_buy SET status = 'FAILED' WHERE id = ?", failed);
        changeAs(2, failed, 6).andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("NOT_JOINABLE"))
                .andExpect(jsonPath("$.reason").value("FAILED"));

        long gone = openGroupBuy(com.camgong.support.GroupBuyBodies.with(com.camgong.support.GroupBuyBodies.valid(), "title", "취소될 공구"));
        joinAs(2, gone, 6).andExpect(status().isCreated());
        jdbc.update("UPDATE group_buy SET status = 'CANCELLED' WHERE id = ?", gone);
        changeAs(2, gone, 6).andExpect(status().isNotFound());
    }

    @Test
    void theSameQuantityWritesNothingAndIgnoresAChangedPrice() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());
        long version = jdbc.queryForObject("SELECT version FROM group_buy WHERE id = ?", Long.class, id);
        java.time.LocalDateTime updated = jdbc.queryForObject("SELECT updated_at FROM group_buy WHERE id = ?",
                java.time.LocalDateTime.class, id);
        clock.advance(java.time.Duration.ofMinutes(5));
        jdbc.update("UPDATE group_buy SET unit_price = 1500 WHERE id = ?", id);           // 가격이 바뀐 뒤

        // 같은 수량 재시도는 가격 확인도 쓰기도 없이 200 (POST 의 같은 수량 재시도와 같은 규칙)
        changeAs(2, id, 6, 1200).andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(6));

        assertThat(jdbc.queryForObject("SELECT version FROM group_buy WHERE id = ?", Long.class, id)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM group_buy WHERE id = ?", java.time.LocalDateTime.class, id))
                .isEqualTo(updated);
        assertThat(secured(id)).isEqualTo(15);
    }
}

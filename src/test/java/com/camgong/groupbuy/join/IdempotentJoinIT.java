package com.camgong.groupbuy.join;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** quickstart 시나리오 4, 5 와 재시도 규칙(decisions-pending #1) */
class IdempotentJoinIT extends JoinTestBase {

    @BeforeEach
    void members() {
        seedMembers(4, 10);
    }

    @Test
    void sameQuantityAgainIsA200WithNoChange() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());

        joinAs(2, id, 6).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(6))
                .andExpect(jsonPath("$.securedQuantity").value(15))
                .andExpect(jsonPath("$.amount").value(7200));
        assertThat(secured(id)).isEqualTo(15);
        assertThat(participationRows(id)).isEqualTo(2);
    }

    @Test
    void differentQuantityAgainIsAlreadyJoined() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());

        joinAs(2, id, 8).andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("ALREADY_JOINED"));
        assertThat(secured(id)).isEqualTo(15);
    }

    @Test
    void retryAfterTheLastSeatMadeItCompletedStillSucceeds() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 15).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("COMPLETED"));

        // 응답을 못 받고 같은 요청을 다시 보낸 경우: 공구가 이미 COMPLETED 여도 성공을 돌려준다
        joinAs(2, id, 15).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.quantity").value(15));
        assertThat(events.completed()).hasSize(1);                         // 이벤트는 다시 나가지 않는다
    }

    @Test
    void oldParticipationOfAFailedGroupBuyIsNotJoinable() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());
        jdbc.update("UPDATE group_buy SET status = 'FAILED' WHERE id = ?", id);

        joinAs(2, id, 6).andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("NOT_JOINABLE"))
                .andExpect(jsonPath("$.reason").value("FAILED"));
    }

    @Test
    void retryOnACancelledGroupBuyIs404() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());
        jdbc.update("UPDATE group_buy SET status = 'CANCELLED' WHERE id = ?", id);

        joinAs(2, id, 6).andExpect(status().isNotFound());
    }

    @Test
    void sameMemberDoubleSubmitWithLittleRoomSucceedsTwiceWithoutDoubleCounting() throws Exception {
        long id = openGroupBuy();
        joinAs(3, id, 8).andExpect(status().isCreated());              // 남은 수량 7

        // 남은 7에서 5개를 동시에 두 번: 하나는 201, 다른 하나는 거부가 아니라 "같은 수량이면 200"이어야 한다
        for (int round = 0; round < 10; round++) {
            jdbc.update("DELETE FROM participation WHERE member_id = 2");
            jdbc.update("UPDATE group_buy SET secured_quantity = 17 WHERE id = ?", id);
            java.util.List<Integer> statuses = com.camgong.support.Concurrent.all(java.util.List.of(
                    () -> joinAs(2, id, 5).andReturn().getResponse().getStatus(),
                    () -> joinAs(2, id, 5).andReturn().getResponse().getStatus()));
            assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(200, 201);
            assertThat(secured(id)).isEqualTo(22);
            assertThat(i1Violations()).isEmpty();
        }
    }
}

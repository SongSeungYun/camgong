package com.camgong.groupbuy.join;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** quickstart 시나리오 1, 2, 3, 6, 7, 8, 14, 15, 20 */
class JoinParticipationIT extends JoinTestBase {

    @BeforeEach
    void members() {
        seedMembers(4, 10);
    }

    @Test
    void joiningAddsTheQuantityAndReportsTheAmountAndRemaining() throws Exception {
        long id = openGroupBuy();

        joinAs(2, id, 6).andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupBuyId").value(id))
                .andExpect(jsonPath("$.quantity").value(6))
                .andExpect(jsonPath("$.amount").value(7200))
                .andExpect(jsonPath("$.securedQuantity").value(15))
                .andExpect(jsonPath("$.remainingQuantity").value(9))
                .andExpect(jsonPath("$.status").value("OPEN"));

        assertThat(secured(id)).isEqualTo(15);
        assertThat(participationRows(id)).isEqualTo(2);
        assertThat(i1Violations()).isEmpty();
        assertThat(events.completed()).isEmpty();                       // 일반 참여는 이벤트가 없다
    }

    @Test
    void fillingExactlyCompletesOnceAndNotifiesEveryoneIncludingTheOrganizer() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());

        joinAs(3, id, 9).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.remainingQuantity").value(0));

        assertThat(statusOf(id)).isEqualTo("COMPLETED");
        assertThat(events.completed()).hasSize(1);
        assertThat(events.completed().get(0).groupBuyId()).isEqualTo(id);
        assertThat(events.completed().get(0).recipientIds()).containsExactlyInAnyOrder(ORGANIZER, 2L, 3L);

        joinAs(4, id, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("NOT_JOINABLE"))
                .andExpect(jsonPath("$.reason").value("COMPLETED"));
        assertThat(events.completed()).hasSize(1);
    }

    @Test
    void overTheRemainingQuantityIsRejectedWithTheRemainingNumber() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 6).andExpect(status().isCreated());                // 남은 수량 9

        joinAs(3, id, 10).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("INSUFFICIENT_QUANTITY"))
                .andExpect(jsonPath("$.remainingQuantity").value(9));
        joinAs(3, id, 16).andExpect(status().isConflict()).andExpect(jsonPath("$.remainingQuantity").value(9));
        joinAs(3, id, 9).andExpect(status().isCreated());                // 경계: 남은 수량과 같으면 통과

        assertThat(secured(id)).isEqualTo(24);
        assertThat(participationRows(id)).isEqualTo(3);
        assertThat(i1Violations()).isEmpty();
    }

    @Test
    void hugeQuantitiesAreRejectedAsInsufficientNotAsOverflowOrServerError() throws Exception {
        long id = openGroupBuy();                                             // 확보 9, 남은 15
        // secured + delta 로 비교하면 int 가 넘쳐 음수가 되어 한도 안으로 오판한다
        for (int q : new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE - 8, Integer.MAX_VALUE - 9, 16 }) {
            joinAs(2, id, q).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("INSUFFICIENT_QUANTITY"))
                    .andExpect(jsonPath("$.remainingQuantity").value(15));
        }
        assertThat(secured(id)).isEqualTo(9);
        assertThat(participationRows(id)).isEqualTo(1);
    }

    @Test
    void aQuantityGivenAsAJsonStringIsCoercedToANumber() throws Exception {
        // 현재 동작을 고정한다(계약에도 적힘): "5" 는 숫자 5 로 받아들여진다. 거부하려면 Jackson 의 스칼라 강제 변환 설정을 끈다.
        long id = openGroupBuy();
        mockMvc.perform(post("/api/groupbuys/" + id + "/participations").with(as(2L))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":\"5\",\"expectedUnitPrice\":1200}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.quantity").value(5));
    }

    @Test
    void organizerCannotJoinHisOwnGroupBuy() throws Exception {
        long id = openGroupBuy();
        joinAs(ORGANIZER, id, 1).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("ORGANIZER_NOT_ALLOWED"))
                .andExpect(jsonPath("$.detail").value("개설자는 직접 참여할 수 없어요. 수량은 공구 수정에서 바꿔요."));
        assertThat(secured(id)).isEqualTo(9);
    }

    @Test
    void withoutLoginIs401() throws Exception {
        long id = openGroupBuy();
        mockMvc.perform(post("/api/groupbuys/" + id + "/participations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1,\"expectedUnitPrice\":1200}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/groupbuys/" + id + "/participations").header("X-Member-Id", "2")
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1,\"expectedUnitPrice\":1200}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrCancelledGroupBuyIs404() throws Exception {
        long id = openGroupBuy();
        cancelAs(ORGANIZER, id).andExpect(status().isNoContent());
        joinAs(2, id, 1).andExpect(status().isNotFound());
        joinAs(2, 99999, 1).andExpect(status().isNotFound());
    }

    @Test
    void invalidBodyIs400WithFieldNames() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 0).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("quantity")));
        joinAs(2, id, -3).andExpect(status().isBadRequest());
        joinAs(2, id, 1, 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("expectedUnitPrice")));
        mockMvc.perform(post("/api/groupbuys/" + id + "/participations").with(as(2L))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("quantity")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("expectedUnitPrice")));
        mockMvc.perform(post("/api/groupbuys/" + id + "/participations").with(as(2L))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":\"x\",\"expectedUnitPrice\":1200}"))
                .andExpect(status().isBadRequest());
        assertThat(secured(id)).isEqualTo(9);
    }

    @Test
    void fractionalQuantityOrPriceIsRejectedNotTruncated() throws Exception {
        long id = openGroupBuy();
        for (String json : new String[] { "{\"quantity\":1.5,\"expectedUnitPrice\":1200}",
                "{\"quantity\":2,\"expectedUnitPrice\":1200.7}" }) {
            mockMvc.perform(post("/api/groupbuys/" + id + "/participations").with(as(2L))
                    .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
        }
        assertThat(secured(id)).isEqualTo(9);
    }

    @Test
    void staleUnitPriceIsRejectedWithTheCurrentPrice() throws Exception {
        long id = openGroupBuy();
        jdbc.update("UPDATE group_buy SET unit_price = 1500 WHERE id = ?", id);

        joinAs(2, id, 1, 1200).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.currentUnitPrice").value(1500));
        joinAs(2, id, 1, 1500).andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(1500));
    }

    @Test
    void completedOrPastDeadlineGroupBuyRejectsWithTheReason() throws Exception {
        long done = openGroupBuy();
        jdbc.update("UPDATE group_buy SET status = 'COMPLETED' WHERE id = ?", done);
        joinAs(2, done, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("NOT_JOINABLE"))
                .andExpect(jsonPath("$.reason").value("COMPLETED"));

        long late = openGroupBuy(com.camgong.support.GroupBuyBodies.with(com.camgong.support.GroupBuyBodies.valid(),
                "title", "마감 지나는 공구"));
        clock.advance(Duration.ofDays(7).plusSeconds(1));
        joinAs(2, late, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("NOT_JOINABLE"))
                .andExpect(jsonPath("$.reason").value("DEADLINE_PASSED"));

        long failed = openGroupBuy(com.camgong.support.GroupBuyBodies.with(com.camgong.support.GroupBuyBodies.valid(clock.instant().plus(Duration.ofDays(3))),
                "title", "실패 공구"));
        jdbc.update("UPDATE group_buy SET status = 'FAILED' WHERE id = ?", failed);
        joinAs(3, failed, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("FAILED"));
    }

    @Test
    void deadlineBoundaryIsExclusive() throws Exception {
        long id = openGroupBuy();
        clock.advance(Duration.ofDays(7).minusSeconds(1));
        joinAs(2, id, 1).andExpect(status().isCreated());                       // 마감 1초 전은 통과

        clock.advance(Duration.ofSeconds(1));                                     // 마감 시각 정각
        joinAs(3, id, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("DEADLINE_PASSED"));
    }

    @Test
    void failedJoinLeavesNothingBehind() throws Exception {
        long id = openGroupBuy();
        joinAs(2, id, 16).andExpect(status().isConflict());
        assertThat(participationRows(id)).isEqualTo(1);
        assertThat(secured(id)).isEqualTo(9);
        assertThat(i1Violations()).isEmpty();
    }
}

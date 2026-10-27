package com.camgong.groupbuy.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** quickstart 시나리오 2, 3, 6, 7, 8, 9, 19 */
class CheckConfirmIT extends CheckTestBase {

    @Test
    void paymentCanBeSetAndRevertedAndTheSummaryFollows() throws Exception {
        long id = completedGroupBuy();

        pay(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(PARTICIPANT))
                .andExpect(jsonPath("$.paidConfirmedAt").value("2026-10-27T09:00:00+09:00"))
                .andExpect(jsonPath("$.receivedConfirmedAt").value((Object) null))
                .andExpect(jsonPath("$.receivedWithoutPayment").value(false));
        sheet(ORGANIZER, id, "").andExpect(jsonPath("$.summary.paidCount").value(1))
                .andExpect(jsonPath("$.summary.amountConfirmed").value(7200));

        pay(ORGANIZER, id, PARTICIPANT, FALSE).andExpect(status().isOk()).andExpect(jsonPath("$.paidConfirmedAt").value((Object) null));
        sheet(ORGANIZER, id, "").andExpect(jsonPath("$.summary.paidCount").value(0))
                .andExpect(jsonPath("$.summary.amountConfirmed").value(0));
        assertThat(paidAt(id, PARTICIPANT)).isNull();
    }

    @Test
    void settingTheSameStateTwiceKeepsTheFirstTimestamp() throws Exception {
        long id = completedGroupBuy();
        pay(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk());
        LocalDateTime first = paidAt(id, PARTICIPANT);

        clock.advance(Duration.ofMinutes(30));
        pay(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk())
                .andExpect(jsonPath("$.paidConfirmedAt").value("2026-10-27T09:00:00+09:00"));      // 두 번째 호출이 시각을 바꾸지 않는다

        assertThat(paidAt(id, PARTICIPANT)).isEqualTo(first).isEqualTo(LocalDateTime.ofInstant(START, ZoneOffset.UTC));
        // 되돌리기도 멱등: 이미 확인 전인 행을 또 되돌려도 오류가 아니다
        pay(ORGANIZER, id, OUTSIDER, FALSE).andExpect(status().isOk());
        pay(ORGANIZER, id, OUTSIDER, FALSE).andExpect(status().isOk());
        // 되돌린 뒤 다시 지정하면 새 시각이 된다
        pay(ORGANIZER, id, PARTICIPANT, FALSE).andExpect(status().isOk());
        pay(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk())
                .andExpect(jsonPath("$.paidConfirmedAt").value("2026-10-27T09:30:00+09:00"));
    }

    @Test
    void receiptIsIndependentOfPayment() throws Exception {
        long id = completedGroupBuy();

        receive(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedConfirmedAt").value("2026-10-27T09:00:00+09:00"))
                .andExpect(jsonPath("$.paidConfirmedAt").value((Object) null))
                .andExpect(jsonPath("$.receivedWithoutPayment").value(true));
        sheet(ORGANIZER, id, "").andExpect(jsonPath("$.summary.receivedCount").value(1))
                .andExpect(jsonPath("$.summary.paidCount").value(0));

        pay(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk()).andExpect(jsonPath("$.receivedWithoutPayment").value(false));
        pay(ORGANIZER, id, PARTICIPANT, FALSE).andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedConfirmedAt").value("2026-10-27T09:00:00+09:00"));  // 입금을 되돌려도 수령은 그대로
        receive(ORGANIZER, id, PARTICIPANT, FALSE).andExpect(status().isOk()).andExpect(jsonPath("$.receivedConfirmedAt").value((Object) null));
        assertThat(paidAt(id, PARTICIPANT)).isNull();
    }

    @Test
    void checkingEveryoneMakesTheConfirmedAmountEqualTheAmountDue() throws Exception {
        long id = completedGroupBuy();
        for (long m : new long[] { PARTICIPANT, OUTSIDER }) {
            pay(ORGANIZER, id, m, TRUE).andExpect(status().isOk());
            receive(ORGANIZER, id, m, TRUE).andExpect(status().isOk());
        }
        sheet(ORGANIZER, id, "").andExpect(jsonPath("$.summary.paidCount").value(2))
                .andExpect(jsonPath("$.summary.receivedCount").value(2))
                .andExpect(jsonPath("$.summary.amountConfirmed").value(18000))
                .andExpect(jsonPath("$.summary.amountDue").value(18000));
    }

    @Test
    void checkingNeverPublishesAnyEventAndTouchesNothingElse() throws Exception {
        long id = completedGroupBuy();
        long version = jdbc.queryForObject("SELECT version FROM group_buy WHERE id = ?", Long.class, id);

        pay(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk());
        receive(ORGANIZER, id, OUTSIDER, TRUE).andExpect(status().isOk());

        assertThat(events.completed()).isEmpty();
        assertThat(events.changed()).isEmpty();
        assertThat(events.cancelled()).isEmpty();
        assertThat(events.failed()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT version FROM group_buy WHERE id = ?", Long.class, id)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT quantity FROM participation WHERE group_buy_id = ? AND member_id = ?",
                Integer.class, id, PARTICIPANT)).isEqualTo(6);
    }
}

package com.camgong.groupbuy.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

import com.camgong.support.Concurrent;

/** quickstart 시나리오 16: 같은 참여자에 대한 true/false 동시 요청. 반복은 -Drace.repeat (CI 기본 20, 수동 1,000). */
class CheckConcurrencyIT extends CheckTestBase {

    private static final int REPEAT = Integer.getInteger("race.repeat", 20);

    @Test
    void concurrentTrueAndFalseRequestsAlwaysEndInAConsistentStateWithoutErrors() throws Exception {
        for (int round = 0; round < REPEAT; round++) {
            jdbc.update("DELETE FROM participation");
            jdbc.update("DELETE FROM group_buy");
            long id = completedGroupBuy();

            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String body = i % 2 == 0 ? TRUE : FALSE;
                calls.add(() -> pay(ORGANIZER, id, PARTICIPANT, body).andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = Concurrent.all(calls);

            assertThat(statuses).as("round %d", round).containsOnly(200);
            LocalDateTime paid = paidAt(id, PARTICIPANT);
            assertThat(paid == null || paid.equals(LocalDateTime.ofInstant(START, ZoneOffset.UTC))).isTrue();
            // 체크표가 DB 와 같은 상태를 보여 준다
            sheet(ORGANIZER, id, "").andExpect(jsonPath("$.summary.paidCount").value(paid == null ? 0 : 1));
            // 다른 필드·다른 참여자는 영향을 받지 않았다
            assertThat(receivedAt(id, PARTICIPANT)).isNull();
            assertThat(paidAt(id, OUTSIDER)).isNull();
        }
    }

    @Test
    void manyConcurrentTrueRequestsKeepTheFirstTimestamp() throws Exception {
        for (int round = 0; round < REPEAT; round++) {
            jdbc.update("DELETE FROM participation");
            jdbc.update("DELETE FROM group_buy");
            clock.set(START);
            long id = completedGroupBuy();
            pay(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk());       // 첫 확인은 START
            clock.advance(java.time.Duration.ofMinutes(10));                          // 이후 요청의 now 는 START 와 다르다

            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                calls.add(() -> pay(ORGANIZER, id, PARTICIPANT, TRUE).andReturn().getResponse().getStatus());
            }
            assertThat(Concurrent.all(calls)).containsOnly(200);
            // COALESCE(paid_at, :now) 가 아니라 paid_at = :now 였다면 마지막 요청의 시각으로 덮어써졌을 것이다
            assertThat(paidAt(id, PARTICIPANT)).isEqualTo(LocalDateTime.ofInstant(START, ZoneOffset.UTC));
        }
    }

    @Test
    void paymentAndReceiptRequestsAtOnceDoNotOverwriteEachOther() throws Exception {
        for (int round = 0; round < REPEAT; round++) {
            jdbc.update("DELETE FROM participation");
            jdbc.update("DELETE FROM group_buy");
            long id = completedGroupBuy();

            Concurrent.all(List.<Callable<Integer>>of(
                    () -> pay(ORGANIZER, id, PARTICIPANT, TRUE).andReturn().getResponse().getStatus(),
                    () -> receive(ORGANIZER, id, PARTICIPANT, TRUE).andReturn().getResponse().getStatus()));

            assertThat(paidAt(id, PARTICIPANT)).as("round %d 입금", round).isNotNull();
            assertThat(receivedAt(id, PARTICIPANT)).as("round %d 수령", round).isNotNull();
        }
    }
}

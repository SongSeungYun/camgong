package com.camgong.groupbuy.close;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.camgong.support.Concurrent;
import com.camgong.support.GroupBuyBodies;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * quickstart 시나리오 11, 12, 13 — 헌법 IV 필수: 모집 완료/실패 전환 경쟁. 반복 횟수는 -Drace.repeat (CI 기본 20, 수동 1,000).
 * 요청 쪽(참여·수정·취소)은 시계를 마감 직전으로 두고 보내고, 확정은 같은 순간에 마감 뒤 시각(T + 2초)으로 직접 호출한다:
 * 처리 순서만 다를 뿐 두 쪽이 같은 공구 행 락에서 직렬화되므로, 먼저 락을 잡은 쪽이 기준이 되고 종료 상태는 정확히 하나다.
 */
class CloseRaceIT extends CloseTestBase {

    private static final int REPEAT = Integer.getInteger("race.repeat", 20);

    @Autowired
    GroupBuyCloser closer;

    @BeforeEach
    void members() {
        seedMembers(4, 6);
    }

    /**
     * 확정 호출은 HTTP 요청 경로(필터·직렬화)보다 훨씬 빨라서 그냥 동시에 출발시키면 늘 확정이 먼저 락을 잡는다. 0~20ms 의 무작위
     * 지연으로 확정이 요청보다 앞서기도 뒤서기도 하게 만들어 두 순서를 모두 겪는다.
     */
    private GroupBuyCloser.Outcome closeAfterDeadline(long id) {
        java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 20_000_000));
        Instant now = T.plusSeconds(2);
        return closer.closeOne(id, now.minus(Duration.ofSeconds(1)), now);
    }

    /** 남은 1, 마감 T. 요청 쪽 시계는 마감 1초 전. */
    private long lastSeatGroupBuy(int round) {
        long id = seed("경쟁 " + round, "OPEN", T, 24, 23);
        clock.set(T.minusSeconds(1));
        return id;
    }

    @Test
    void lastSeatJoinRacingTheCloseEndsInExactlyOneTerminalState() throws Exception {
        int completed = 0;
        int failed = 0;
        for (int round = 0; round < REPEAT; round++) {
            events.clear();
            long id = lastSeatGroupBuy(round);

            List<Object> results = Concurrent.all(List.<Callable<Object>>of(
                    () -> mockMvc.perform(post("/api/groupbuys/" + id + "/participations").with(as(4))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1,\"expectedUnitPrice\":1200}"))
                            .andReturn().getResponse().getStatus(),
                    () -> closeAfterDeadline(id)));

            int joinStatus = (Integer) results.get(0);
            Object closeOutcome = results.get(1);
            String status = statusOf(id);
            assertThat(status).as("round %d", round).isIn("COMPLETED", "FAILED");
            if (status.equals("COMPLETED")) {
                completed++;
                assertThat(joinStatus).isEqualTo(201);
                assertThat(closeOutcome).isEqualTo(GroupBuyCloser.Outcome.SKIPPED);        // 가득 찬 공구는 확정 대상이 아니다
                assertThat(events.completed()).hasSize(1);
                assertThat(events.failed()).isEmpty();
            } else {
                failed++;
                assertThat(joinStatus).isEqualTo(409);
                assertThat(closeOutcome).isEqualTo(GroupBuyCloser.Outcome.CLOSED);
                assertThat(events.failed()).hasSize(1);
                assertThat(events.completed()).isEmpty();
            }
            assertThat(wronglyFailed(T.plusSeconds(2))).isEmpty();
            assertThat(jdbc.queryForObject("SELECT secured_quantity - (SELECT COALESCE(SUM(quantity), 0) FROM participation "
                    + "WHERE group_buy_id = ?) FROM group_buy WHERE id = ?", Integer.class, id, id)).isZero();   // I1
        }
        System.out.println("[CloseRaceIT last seat] rounds=" + REPEAT + " completed=" + completed + " failed=" + failed);
        assertBothOrdersSeen("마지막 자리", completed, failed);
    }

    @Test
    void deadlineExtensionRacingTheCloseEndsInExactlyOneWinner() throws Exception {
        int extended = 0;
        int closedFirst = 0;
        for (int round = 0; round < REPEAT; round++) {
            events.clear();
            long id = seed("연장 경쟁 " + round, "OPEN", T, 24, 9);
            clock.set(T.minusSeconds(1));
            var body = GroupBuyBodies.with(GroupBuyBodies.valid(T.plus(Duration.ofDays(3))), "title", "연장 경쟁 " + round);

            List<Object> results = Concurrent.all(List.<Callable<Object>>of(
                    () -> updateAs(ORGANIZER, id, body).andReturn().getResponse().getStatus(),
                    () -> closeAfterDeadline(id)));

            int updateStatus = (Integer) results.get(0);
            String status = statusOf(id);
            if (status.equals("OPEN")) {                                        // 연장이 먼저: 확정은 건너뛴다
                extended++;
                assertThat(updateStatus).isEqualTo(200);
                assertThat(results.get(1)).isEqualTo(GroupBuyCloser.Outcome.SKIPPED);
                assertThat(events.failed()).isEmpty();
            } else {                                                           // 확정이 먼저: 연장은 409
                closedFirst++;
                assertThat(status).isEqualTo("FAILED");
                assertThat(updateStatus).isEqualTo(409);
                assertThat(events.failed()).hasSize(1);
            }
            assertThat(wronglyFailed(T.plusSeconds(2))).as("round %d", round).isEmpty();
        }
        System.out.println("[CloseRaceIT extension] rounds=" + REPEAT + " extended=" + extended + " closedFirst=" + closedFirst);
        assertBothOrdersSeen("마감 연장", extended, closedFirst);
    }

    @Test
    void organizerCancelRacingTheCloseEndsAsEitherCancelledOrFailedNeverBoth() throws Exception {
        int cancelled = 0;
        int failed = 0;
        for (int round = 0; round < REPEAT; round++) {
            events.clear();
            long id = seed("취소 경쟁 " + round, "OPEN", T, 24, 9);
            addParticipant(id, 4, 2);
            clock.set(T.minusSeconds(1));

            List<Object> results = Concurrent.all(List.<Callable<Object>>of(
                    () -> cancelAs(ORGANIZER, id).andReturn().getResponse().getStatus(),
                    () -> closeAfterDeadline(id)));

            int cancelStatus = (Integer) results.get(0);
            String status = statusOf(id);
            assertThat(status).as("round %d", round).isIn("CANCELLED", "FAILED");
            if (status.equals("CANCELLED")) {
                cancelled++;
                assertThat(cancelStatus).isEqualTo(204);
                assertThat(results.get(1)).isEqualTo(GroupBuyCloser.Outcome.SKIPPED);
                assertThat(events.failed()).isEmpty();
                assertThat(events.cancelled()).hasSize(1);
            } else {
                failed++;
                assertThat(cancelStatus).isEqualTo(409);
                assertThat(events.failed()).hasSize(1);
                assertThat(events.cancelled()).isEmpty();
            }
        }
        System.out.println("[CloseRaceIT cancel] rounds=" + REPEAT + " cancelled=" + cancelled + " failed=" + failed);
        assertBothOrdersSeen("개설자 취소", cancelled, failed);
    }

    /**
     * 반복이 충분히 많을 때(수동 -Drace.repeat=…)는 두 순서가 모두 나왔는지 단언한다 — 한쪽만 나오면 이 테스트가 경쟁이 아니라 한 순서만
     * 확인하는 퇴화 상태라는 뜻이다. 소수 쪽이 10% 안팎이라 200회 이상에서는 한쪽이 0일 확률이 사실상 없다. 순서를 결정적으로 고정한
     * 확인은 CloseOrderedRaceIT 가 한다.
     */
    private static void assertBothOrdersSeen(String name, int first, int second) {
        if (REPEAT >= 200) {
            assertThat(first).as(name + ": 요청이 먼저 이긴 횟수").isPositive();
            assertThat(second).as(name + ": 확정이 먼저 이긴 횟수").isPositive();
        }
    }

    @Test
    void participantLeavingRacingTheCloseNeverLeavesAnInconsistentGroupBuy() throws Exception {
        for (int round = 0; round < REPEAT; round++) {
            events.clear();
            long id = seed("이탈 경쟁 " + round, "OPEN", T, 24, 9);
            addParticipant(id, 4, 3);
            clock.set(T.minusSeconds(1));

            List<Object> results = Concurrent.all(List.<Callable<Object>>of(
                    () -> mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/groupbuys/" + id + "/participations/me").with(as(4))).andReturn().getResponse().getStatus(),
                    () -> closeAfterDeadline(id)));

            int leaveStatus = (Integer) results.get(0);
            if (results.get(1) == GroupBuyCloser.Outcome.SKIPPED) {
                // 이탈이 공구 행 락을 쥐고 있을 때 확정이 도착해 건너뛰었다(SKIP LOCKED). 이탈은 성공했고 공구는 다음 실행이 확정한다.
                assertThat(leaveStatus).isEqualTo(204);
                assertThat(statusOf(id)).isEqualTo("OPEN");
                assertThat(events.failed()).isEmpty();
                Instant later = T.plusSeconds(2);
                assertThat(closer.closeOne(id, later.minusSeconds(1), later)).isEqualTo(GroupBuyCloser.Outcome.CLOSED);
            }
            assertThat(statusOf(id)).as("round %d", round).isEqualTo("FAILED");         // 미충족 공구는 결국 FAILED
            assertThat(leaveStatus).isIn(204, 409);
            assertThat(events.failed()).hasSize(1);
            if (leaveStatus == 204) {                                                   // 이탈이 먼저: 수신자에서 빠진다
                assertThat(events.failed().get(0).recipientIds()).containsExactly(ORGANIZER);
                assertThat(participationRows(id)).isEqualTo(1);
            } else {                                                                    // 확정이 먼저: 이탈은 거부되고 참여가 남는다
                assertThat(events.failed().get(0).recipientIds()).containsExactlyInAnyOrder(ORGANIZER, 4L);
                assertThat(participationRows(id)).isEqualTo(2);
            }
            assertThat(jdbc.queryForObject("SELECT secured_quantity - (SELECT COALESCE(SUM(quantity), 0) FROM participation "
                    + "WHERE group_buy_id = ?) FROM group_buy WHERE id = ?", Integer.class, id, id)).isZero();
        }
    }

    @Test
    void quantityChangeThatFillsRacingTheCloseEndsInExactlyOneTerminalState() throws Exception {
        for (int round = 0; round < REPEAT; round++) {
            events.clear();
            long id = seed("변경 경쟁 " + round, "OPEN", T, 24, 9);
            addParticipant(id, 4, 5);                                                   // 확보 14, 남은 10
            clock.set(T.minusSeconds(1));

            List<Object> results = Concurrent.all(List.<Callable<Object>>of(
                    () -> mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/groupbuys/" + id + "/participations/me").with(as(4)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"quantity\":15,\"expectedUnitPrice\":1200}")).andReturn().getResponse().getStatus(),
                    () -> closeAfterDeadline(id)));

            int change = (Integer) results.get(0);
            String status = statusOf(id);
            assertThat(status).as("round %d", round).isIn("COMPLETED", "FAILED");
            assertThat(change).isEqualTo(status.equals("COMPLETED") ? 200 : 409);
            assertThat(events.completed().size() + events.failed().size()).isEqualTo(1);
            assertThat(wronglyFailed(T.plusSeconds(2))).isEmpty();
        }
    }
}

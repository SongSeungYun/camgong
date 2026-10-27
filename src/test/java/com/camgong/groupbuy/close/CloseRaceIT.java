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
    }
}

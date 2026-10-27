package com.camgong.groupbuy.close;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCancelled;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyChanged;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCompleted;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyFailed;
import com.camgong.support.GroupBuyBodies;

/**
 * 마감 확정과 요청(참여·수량 변경·수정·취소)이 같은 공구에 겹치는 두 가지 순서를 결정적으로 고정해 확인한다:
 * (A) 확정이 락을 쥐고 있을 때 요청이 도착한다 → 요청은 락을 기다렸다가 FAILED 를 보고 409.
 * (B) 요청이 락을 쥐고 있을 때 확정이 도착한다 → 확정은 SKIP LOCKED 로 건너뛰고, 요청의 결과가 남는다.
 * 무작위 지연 경쟁(CloseRaceIT)은 두 순서를 모두 겪는다는 보장이 없어서 이 테스트가 순서를 책임진다.
 */
@Import(PausingListener.Config.class)
class CloseOrderedRaceIT extends CloseTestBase {

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @Autowired
    GroupBuyCloser closer;

    @BeforeEach
    void members() {
        seedMembers(4, 6);
    }

    @AfterEach
    void cleanUp() {
        PausingListener.disarm();
        pool.shutdownNow();
    }

    private GroupBuyCloser.Outcome closeAfterDeadline(long id) {
        Instant now = T.plusSeconds(2);
        return closer.closeOne(id, now.minusSeconds(1), now);
    }

    private int join(long id, long member, int qty) throws Exception {
        return mockMvc.perform(post("/api/groupbuys/" + id + "/participations").with(as(member))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + qty + ",\"expectedUnitPrice\":1200}"))
                .andReturn().getResponse().getStatus();
    }

    private int change(long id, long member, int qty) throws Exception {
        return mockMvc.perform(put("/api/groupbuys/" + id + "/participations/me").with(as(member))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + qty + ",\"expectedUnitPrice\":1200}"))
                .andReturn().getResponse().getStatus();
    }

    private int extend(long id) throws Exception {
        return updateAs(ORGANIZER, id, GroupBuyBodies.with(GroupBuyBodies.valid(T.plus(Duration.ofDays(3))), "title", "연장"))
                .andReturn().getResponse().getStatus();
    }

    private int cancel(long id) throws Exception {
        return cancelAs(ORGANIZER, id).andReturn().getResponse().getStatus();
    }

    /** (A) 확정이 락을 쥐고 멈춘 사이에 요청을 보낸다. 요청이 락에서 막힌 뒤 확정을 풀어 준다. */
    private int requestWhileCloseHoldsTheLock(long id, Callable<Integer> request) throws Exception {
        PausingListener.Pause pause = PausingListener.arm(GroupBuyFailed.class);
        Future<GroupBuyCloser.Outcome> close = pool.submit(() -> closeAfterDeadline(id));
        pause.awaitReached();
        Future<Integer> req = pool.submit(request);
        awaitLockWait(10_000);                                     // 요청이 확정이 쥔 공구 행 락에서 실제로 막혔다
        assertThat(req.isDone()).as("요청은 확정이 쥔 락 때문에 아직 끝나지 않았다").isFalse();
        pause.release();
        assertThat(close.get()).isEqualTo(GroupBuyCloser.Outcome.CLOSED);
        return req.get();
    }

    /** (B) 요청이 락을 쥐고 멈춘 사이에 확정이 도착한다. */
    private int closeWhileRequestHoldsTheLock(long id, Class<?> requestEvent, Callable<Integer> request) throws Exception {
        PausingListener.Pause pause = PausingListener.arm(requestEvent);
        Future<Integer> req = pool.submit(request);
        pause.awaitReached();
        assertThat(closeAfterDeadline(id)).as("락이 잡힌 공구는 기다리지 않고 건너뛴다").isEqualTo(GroupBuyCloser.Outcome.SKIPPED);
        pause.release();
        return req.get();
    }

    private long lastSeatGroupBuy(String title) {
        long id = seed(title, "OPEN", T, 24, 23);
        clock.set(T.minusSeconds(1));
        return id;
    }

    @Test
    void lastSeatJoin_closeHoldsLock_joinIsRefused() throws Exception {
        long id = lastSeatGroupBuy("A 마지막 자리");
        int status = requestWhileCloseHoldsTheLock(id, () -> join(id, 4, 1));

        assertThat(status).isEqualTo(409);
        assertThat(statusOf(id)).isEqualTo("FAILED");
        assertThat(events.failed()).hasSize(1);
        assertThat(events.completed()).isEmpty();
        assertThat(participationRows(id)).isEqualTo(1);
    }

    @Test
    void lastSeatJoin_joinHoldsLock_closeIsSkippedAndTheGroupBuyCompletes() throws Exception {
        long id = lastSeatGroupBuy("B 마지막 자리");
        int status = closeWhileRequestHoldsTheLock(id, GroupBuyCompleted.class, () -> join(id, 4, 1));

        assertThat(status).isEqualTo(201);
        assertThat(statusOf(id)).isEqualTo("COMPLETED");
        assertThat(events.completed()).hasSize(1);
        assertThat(events.failed()).isEmpty();
    }

    @Test
    void quantityChangeThatFillsTheGroupBuy_bothOrders() throws Exception {
        // 남은 수량 5, 회원 4 가 이미 5 → 10 으로 올려 가득 채우는 PUT
        long a = seed("A 변경", "OPEN", T, 24, 9);
        addParticipant(a, 4, 5);                                    // 확보 14, 남은 10
        clock.set(T.minusSeconds(1));
        assertThat(requestWhileCloseHoldsTheLock(a, () -> change(a, 4, 15))).isEqualTo(409);
        assertThat(statusOf(a)).isEqualTo("FAILED");
        assertThat(events.completed()).isEmpty();

        events.clear();
        PausingListener.disarm();
        long b = seed("B 변경", "OPEN", T, 24, 9);
        addParticipant(b, 4, 5);
        assertThat(closeWhileRequestHoldsTheLock(b, GroupBuyCompleted.class, () -> change(b, 4, 15))).isEqualTo(200);
        assertThat(statusOf(b)).isEqualTo("COMPLETED");
        assertThat(events.completed()).hasSize(1);
        assertThat(events.failed()).isEmpty();
    }

    @Test
    void deadlineExtension_bothOrders() throws Exception {
        long a = seed("A 연장", "OPEN", T, 24, 9);
        addParticipant(a, 4, 2);
        clock.set(T.minusSeconds(1));
        assertThat(requestWhileCloseHoldsTheLock(a, () -> extend(a))).isEqualTo(409);
        assertThat(statusOf(a)).isEqualTo("FAILED");

        events.clear();
        PausingListener.disarm();
        long b = seed("B 연장", "OPEN", T, 24, 9);
        addParticipant(b, 4, 2);
        assertThat(closeWhileRequestHoldsTheLock(b, GroupBuyChanged.class, () -> extend(b))).isEqualTo(200);
        assertThat(statusOf(b)).isEqualTo("OPEN");                    // 연장이 이겨 확정 대상에서 벗어났다
        assertThat(events.failed()).isEmpty();
        // 연장된 마감은 아직 멀었으니 다음 확정 실행도 건드리지 않는다
        clock.set(T.plusSeconds(5));
        assertThat(closeService.closeExpired().closed()).isZero();
        assertThat(statusOf(b)).isEqualTo("OPEN");
    }

    @Test
    void organizerCancel_bothOrders() throws Exception {
        long a = seed("A 취소", "OPEN", T, 24, 9);
        addParticipant(a, 4, 2);
        clock.set(T.minusSeconds(1));
        assertThat(requestWhileCloseHoldsTheLock(a, () -> cancel(a))).isEqualTo(409);
        assertThat(statusOf(a)).isEqualTo("FAILED");
        assertThat(events.cancelled()).isEmpty();

        events.clear();
        PausingListener.disarm();
        long b = seed("B 취소", "OPEN", T, 24, 9);
        addParticipant(b, 4, 2);
        assertThat(closeWhileRequestHoldsTheLock(b, GroupBuyCancelled.class, () -> cancel(b))).isEqualTo(204);
        assertThat(statusOf(b)).isEqualTo("CANCELLED");
        assertThat(events.failed()).isEmpty();
        assertThat(events.cancelled()).hasSize(1);
    }
}

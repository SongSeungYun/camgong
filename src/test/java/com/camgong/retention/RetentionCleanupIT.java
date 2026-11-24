package com.camgong.retention;

import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

import com.camgong.support.IntegrationTestBase;

/** 012 T003~T005: 끝난 지 1년 지난 공구와 그 참여·알림, 그 뒤 함께한 공구가 없는 탈퇴 회원 행을 지운다. */
@ExtendWith(OutputCaptureExtension.class)
class RetentionCleanupIT extends IntegrationTestBase {

    private static final Duration YEAR = Duration.ofDays(365);

    @Autowired
    RetentionCleanup cleanup;

    private LocalDateTime ago(Duration d) {
        return LocalDateTime.ofInstant(clock.instant().minus(d), ZoneOffset.UTC);
    }

    /** 개설자 1 + 참여자 2, 각자 알림 하나. endedAgo 가 null 이면 끝나지 않은 공구. */
    private long groupBuyEnded(String status, Duration endedAgo) {
        return groupBuyEnded(status, endedAgo, ORGANIZER, PARTICIPANT);
    }

    private long groupBuyEnded(String status, Duration endedAgo, long organizer, long participant) {
        Instant deadline = endedAgo == null ? clock.instant().minus(Duration.ofDays(730)) : clock.instant().minus(endedAgo);
        long id = groupBuy().organizer(organizer).status(status).quantities(10, 10).deadline(deadline).insert(jdbc);
        if (endedAgo != null) {
            jdbc.update("UPDATE group_buy SET ended_at = ? WHERE id = ?", ago(endedAgo), id);
        }
        jdbc.update("UPDATE group_buy SET secured_quantity = 4 WHERE id = ?", id);
        participate(id, organizer, 1);
        participate(id, participant, 3);
        notify(id, participant);
        notify(id, organizer);
        return id;
    }

    private void participate(long groupBuyId, long memberId, int quantity) {
        jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, ?, ?, ?)",
                groupBuyId, memberId, quantity, ago(Duration.ofDays(800)));
    }

    private void notify(long groupBuyId, long recipient) {
        jdbc.update("INSERT INTO notification (event_id, recipient_id, type, group_buy_id, group_buy_title, created_at) "
                + "VALUES (UUID(), ?, 'COMPLETED', ?, '공구', ?)", recipient, groupBuyId, ago(Duration.ofDays(1)));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(jdbc.queryForList("SELECT * FROM group_buy ORDER BY id"));
        rows.addAll(jdbc.queryForList("SELECT * FROM participation ORDER BY id"));
        rows.addAll(jdbc.queryForList("SELECT * FROM notification ORDER BY id"));
        return rows;
    }

    private void withdraw(long id) {
        jdbc.update("UPDATE member SET name = '탈퇴한 회원', display_name_key = NULL, email = NULL, email_key = NULL, "
                + "password_hash = 'withdrawn', withdrawn_at = ? WHERE id = ?", ago(Duration.ofDays(400)), id);
    }

    // ---- T003 ----

    @Test
    void groupBuysEndedOverAYearAgoGoWithTheirParticipationsAndNotifications(CapturedOutput output) throws Exception {
        Duration over = YEAR.plusMinutes(1);
        long completed = groupBuyEnded("COMPLETED", over);
        long failed = groupBuyEnded("FAILED", over);
        long cancelled = groupBuyEnded("CANCELLED", over);
        long young = groupBuyEnded("COMPLETED", Duration.ofDays(364));
        long staleOpen = groupBuyEnded("OPEN", null);
        List<Map<String, Object>> keptBefore = snapshotOf(young, staleOpen);

        RetentionCleanup.Result result = cleanup.run();

        assertThat(result.groupBuys()).isEqualTo(3);
        for (long gone : List.of(completed, failed, cancelled)) {
            assertThat(count("SELECT COUNT(*) FROM group_buy WHERE id = ?", gone)).isZero();
            assertThat(count("SELECT COUNT(*) FROM participation WHERE group_buy_id = ?", gone)).isZero();
            assertThat(count("SELECT COUNT(*) FROM notification WHERE group_buy_id = ?", gone)).isZero();
        }
        assertThat(snapshotOf(young, staleOpen)).as("다른 공구 기록은 그대로").isEqualTo(keptBefore);
        assertThat(output).contains("retention-cleanup groupBuys=3 members=0");

        mockMvc.perform(get("/api/groupbuys/" + completed).with(as(PARTICIPANT))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/groupbuys/" + completed + "/check-sheet").with(as(ORGANIZER))).andExpect(status().isNotFound());
        String mine = mockMvc.perform(get("/api/me/groupbuys?role=participant").with(as(PARTICIPANT)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mine).contains("\"id\":" + young).doesNotContain("\"id\":" + completed + ",");
        String list = mockMvc.perform(get("/api/groupbuys?status=all").with(as(PARTICIPANT)))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain("\"id\":" + completed + ",").doesNotContain("\"id\":" + failed + ",");
    }

    private List<Map<String, Object>> snapshotOf(long... ids) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (long id : ids) {
            rows.addAll(jdbc.queryForList("SELECT * FROM group_buy WHERE id = ?", id));
            rows.addAll(jdbc.queryForList("SELECT * FROM participation WHERE group_buy_id = ? ORDER BY id", id));
            rows.addAll(jdbc.queryForList("SELECT * FROM notification WHERE group_buy_id = ? ORDER BY id", id));
        }
        return rows;
    }

    @Test
    void nothingYoungerThanAYearOrUnfinishedIsTouched() {
        groupBuyEnded("COMPLETED", YEAR.minusMinutes(1));
        groupBuyEnded("CANCELLED", Duration.ofDays(1));
        groupBuyEnded("OPEN", null);
        List<Map<String, Object>> before = snapshot();

        assertThat(cleanup.run().groupBuys()).isZero();

        assertThat(snapshot()).isEqualTo(before);
    }

    /** 리뷰: 밀린 공구가 많아도 ended_at 인덱스 범위를 잠그지 않는다(그 끝의 간격이 잠기면 지금 끝나는 공구가 정리를 기다린다). */
    @Test
    void lockingTheChosenGroupBuysUsesThePrimaryKeyOnly() {
        for (int i = 0; i < 30; i++) {
            long id = groupBuy().status("COMPLETED").quantities(2, 2).insert(jdbc);
            jdbc.update("UPDATE group_buy SET ended_at = ? WHERE id = ?", ago(YEAR.plusDays(i)), id);
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM group_buy", Long.class);
        String sql = "EXPLAIN " + RetentionCleanup.LOCK_GROUP_BUYS.replace(":ids", ids.toString().replaceAll("[\\[\\]]", ""))
                .replace(":cut", "'" + ago(YEAR) + "'");
        assertThat(jdbc.queryForMap(sql)).containsEntry("key", "PRIMARY");
    }

    // ---- T004 ----

    @Test
    void withdrawnMembersWithNothingLeftAreDeletedOthersStay(CapturedOutput output) {
        seedMembers(10, 13);
        withdraw(10);                                      // A: 오래된 공구에만 참여했다
        withdraw(11);                                      // B: 1년이 안 된 공구에 참여했다
        // C(12): 탈퇴 안 함, 함께한 공구 없음
        withdraw(13);                                      // D: 공구 없이 탈퇴
        long old = groupBuyEnded("COMPLETED", YEAR.plusDays(1), ORGANIZER, 10);
        groupBuyEnded("COMPLETED", Duration.ofDays(30), ORGANIZER, 11);
        // 탈퇴 때 지웠어야 하지만 남은 행(FK): 같이 지워야 회원 행을 지울 수 있다
        jdbc.update("INSERT INTO login_session (token_hash, member_id, created_at, last_used_at) VALUES (?, 10, ?, ?)",
                "a".repeat(64), ago(Duration.ofDays(400)), ago(Duration.ofDays(400)));
        jdbc.update("INSERT INTO password_reset (email_key, member_id, token_hash, token_expires_at, last_sent_at, "
                + "window_started_at, sent_count, created_at) VALUES ('d@school.ac.kr', 13, NULL, NULL, ?, ?, 1, ?)",
                ago(Duration.ofHours(1)), ago(Duration.ofHours(1)), ago(Duration.ofHours(1)));
        jdbc.update("INSERT INTO create_guard (member_id, fingerprint, created_at) VALUES (13, ?, ?)", "f".repeat(64),
                ago(Duration.ofDays(400)));

        RetentionCleanup.Result result = cleanup.run();

        assertThat(result.groupBuys()).isOne();
        assertThat(result.members()).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM group_buy WHERE id = ?", old)).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM member WHERE id >= 10 ORDER BY id", Long.class)).containsExactly(11L, 12L);
        assertThat(jdbc.queryForList("SELECT id FROM member WHERE id < 10 ORDER BY id", Long.class))
                .as("탈퇴 안 한 회원은 그대로").containsExactly(1L, 2L, 3L);
        assertThat(count("SELECT COUNT(*) FROM login_session WHERE member_id IN (10, 13)")).isZero();
        assertThat(output).contains("retention-cleanup groupBuys=1 members=2");
    }

    @Test
    void aWithdrawnOrganizerOfAnOldGroupBuyGoesOnceItIsGone() {
        seedMembers(20, 21);
        withdraw(20);
        groupBuyEnded("FAILED", YEAR.plusDays(3), 20, 21);
        assertThat(cleanup.run().members()).isOne();
        assertThat(count("SELECT COUNT(*) FROM member WHERE id = 20")).isZero();
        assertThat(count("SELECT COUNT(*) FROM member WHERE id = 21")).isOne();
    }

    // ---- T005 ----

    @Test
    void moreThanOneRoundOfGroupBuysAreAllDeleted() {
        seedMembers(30, 30);
        for (int i = 0; i < 250; i++) {
            long id = groupBuy().status("COMPLETED").quantities(2, 2).insert(jdbc);
            jdbc.update("UPDATE group_buy SET ended_at = ? WHERE id = ?", ago(YEAR.plusHours(1)), id);
            participate(id, ORGANIZER, 1);
            participate(id, 30, 1);
        }
        long young = groupBuyEnded("COMPLETED", Duration.ofDays(10));

        assertThat(cleanup.run().groupBuys()).isEqualTo(250);

        assertThat(jdbc.queryForList("SELECT id FROM group_buy", Long.class)).containsExactly(young);
        assertThat(count("SELECT COUNT(*) FROM participation WHERE member_id = 30")).isZero();
    }

    @Autowired
    org.springframework.transaction.PlatformTransactionManager txManager;

    /**
     * 잠금 순서를 운 없이 본다: 체크 토글처럼 공구 행을 공유 잠금으로 먼저 읽은 트랜잭션이 있을 때 정리는 공구 행에서 기다려야 하고, 그동안
     * 참여 행은 잡지 않아야 한다. 그래야 토글이 이어서 참여 행을 바꿔도 교착하지 않는다(참여부터 지우면 여기서 교착한다).
     */
    @Test
    void theCleanupWaitsOnTheGroupBuyBeforeTouchingParticipations() throws Exception {
        long id = groupBuyEnded("COMPLETED", YEAR.plusDays(1));
        long before = deadlocks();
        var tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> toggle = pool.submit(() -> tx.execute(s -> {
                jdbc.queryForObject("SELECT organizer_id FROM group_buy WHERE id = ? FOR SHARE", Long.class, id);
                locked.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return jdbc.update("UPDATE participation SET paid_at = ? WHERE group_buy_id = ? AND member_id = ?",
                        ago(Duration.ZERO), id, PARTICIPANT);
            }));
            locked.await();
            Future<RetentionCleanup.Result> run = pool.submit(() -> cleanup.run());
            awaitLockWait(5000);
            go.countDown();
            assertThat(toggle.get()).isOne();
            assertThat(run.get().groupBuys()).isOne();
        } finally {
            go.countDown();
            pool.shutdownNow();
        }
        assertThat(deadlocks()).as("교착 없음").isEqualTo(before);
        assertThat(count("SELECT COUNT(*) FROM group_buy WHERE id = ?", id)).isZero();
    }

    /**
     * 정리가 공구 행을 잡은 사이 진짜 체크 토글이 들어온다(리뷰): 토글의 일반 읽기는 스냅샷이라 공구·참여가 보여 통과하고, UPDATE 가 정리를
     * 기다린다. 그 사이 정리가 참여를 지워도 교착하지 않아야 하고(토글이 참여 행을 먼저 잡았다면 여기서 교착한다), 정리가 커밋한 뒤 토글은
     * 없는 공구라 404 여야 한다(500 이 아니다).
     */
    @Test
    void aToggleArrivingWhileTheCleanupHoldsTheGroupBuyGets404WithoutADeadlock() throws Exception {
        long id = groupBuyEnded("COMPLETED", YEAR.plusDays(1));
        long before = deadlocks();
        var tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> sweep = pool.submit(() -> tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT id FROM group_buy WHERE id = ? FOR UPDATE", Long.class, id);
                locked.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                jdbc.update("DELETE FROM notification WHERE group_buy_id = ?", id);
                jdbc.update("DELETE FROM participation WHERE group_buy_id = ?", id);
                jdbc.update("DELETE FROM group_buy WHERE id = ?", id);
            }));
            locked.await();
            Future<Integer> toggle = pool.submit(() -> mockMvc.perform(
                    put("/api/groupbuys/" + id + "/participations/" + PARTICIPANT + "/payment").with(as(ORGANIZER))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                    .andReturn().getResponse().getStatus());
            awaitLockWait(5000);
            go.countDown();
            sweep.get();
            assertThat(toggle.get()).isEqualTo(404);
        } finally {
            go.countDown();
            pool.shutdownNow();
        }
        assertThat(deadlocks()).as("교착 없음").isEqualTo(before);
    }

    /** 006 체크 토글은 끝난 공구를 잠그는 유일한 요청이다. 같이 돌아도 요청이 500 이 되거나 정리가 실패하지 않는다(SC-004). */
    @Test
    void checkTogglesRacingTheCleanupNeverFail() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 40; i++) {
                long id = groupBuyEnded("COMPLETED", YEAR.plusDays(1));
                CountDownLatch go = new CountDownLatch(1);
                Future<Integer> toggle = pool.submit(() -> {
                    go.await();
                    return mockMvc.perform(put("/api/groupbuys/" + id + "/participations/" + PARTICIPANT + "/payment")
                            .with(as(ORGANIZER)).contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                            .andReturn().getResponse().getStatus();
                });
                Future<RetentionCleanup.Result> run = pool.submit(() -> {
                    go.await();
                    return cleanup.run();
                });
                go.countDown();
                assertThat(toggle.get()).isIn(200, 404);
                assertThat(run.get().groupBuys()).isOne();
                assertThat(count("SELECT COUNT(*) FROM group_buy WHERE id = ?", id)).isZero();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}

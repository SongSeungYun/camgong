package com.camgong.groupbuy.close;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.camgong.groupbuy.GroupBuyEvents.GroupBuyFailed;

/** quickstart 시나리오 1~7, 17 */
@ExtendWith(OutputCaptureExtension.class)
class CloseExpiredIT extends CloseTestBase {

    private GroupBuyFailed onlyFailedEvent() {
        assertThat(events.failed()).hasSize(1);
        return events.failed().get(0);
    }

    @Test
    void anExpiredUnderfilledGroupBuyBecomesFailedAndRejectsEveryChange() throws Exception {
        seedMembers(4, 6);
        long id = openUnderfilled("마감 지난 공구");
        addParticipant(id, 2, 3);
        clock.set(T.plusSeconds(2));

        CloseStats stats = closeService.closeExpired();

        assertThat(stats.closed()).isEqualTo(1);
        assertThat(statusOf(id)).isEqualTo("FAILED");
        assertThat(wronglyFailed(clock.instant())).isEmpty();
        // 이후 참여·수량 변경·취소·수정이 모두 409
        var body = com.camgong.support.GroupBuyBodies.valid(T.plus(Duration.ofDays(3)));
        org.assertj.core.api.Assertions.assertThat(
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/groupbuys/" + id + "/participations")
                        .with(as(4)).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1,\"expectedUnitPrice\":1200}")).andReturn().getResponse().getStatus()).isEqualTo(409);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/groupbuys/" + id + "/participations/me")
                .with(as(2)).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1,\"expectedUnitPrice\":1200}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("FAILED"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/groupbuys/" + id + "/participations/me")
                .with(as(2))).andExpect(status().isConflict());
        cancelAs(ORGANIZER, id).andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("FAILED"));
        updateAs(ORGANIZER, id, body).andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("FAILED"));
    }

    @Test
    void nothingChangesBeforeTheDeadline() {
        long id = openUnderfilled("아직 마감 전");
        clock.set(T.minusSeconds(1));

        CloseStats stats = closeService.closeExpired();

        assertThat(stats.closed()).isZero();
        assertThat(statusOf(id)).isEqualTo("OPEN");
        assertThat(events.failed()).isEmpty();
    }

    @Test
    void completedCancelledAndAlreadyFailedAreLeftAlone() {
        long done = seed("완료", "COMPLETED", T, 24, 24);
        long cancelled = seed("취소", "CANCELLED", T, 24, 9);
        long failed = seed("이미 실패", "FAILED", T, 24, 9);
        long filledButOpen = seed("가득 찼지만 OPEN", "OPEN", T, 24, 24);       // 방어: 완료 전환 누락이 있어도 실패로 만들지 않는다
        clock.set(T.plusSeconds(5));

        CloseStats stats = closeService.closeExpired();

        assertThat(stats.closed()).isZero();
        assertThat(statusOf(done)).isEqualTo("COMPLETED");
        assertThat(statusOf(cancelled)).isEqualTo("CANCELLED");
        assertThat(statusOf(failed)).isEqualTo("FAILED");
        assertThat(statusOf(filledButOpen)).isEqualTo("OPEN");
        assertThat(events.failed()).isEmpty();
    }

    @Test
    void theSafetyMarginKeepsTheDeadlineSecondOpenButTheParticipationIsAlreadyRefused() throws Exception {
        long id = openUnderfilled("정각");
        seedMembers(4, 4);

        clock.set(T);                                              // 마감 정각: 참여는 즉시 거부, 확정은 아직
        assertThat(closeService.closeExpired().closed()).isZero();
        assertThat(statusOf(id)).isEqualTo("OPEN");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/groupbuys/" + id + "/participations")
                .with(as(4)).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1,\"expectedUnitPrice\":1200}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("DEADLINE_PASSED"));

        clock.set(T.plusSeconds(1));                               // 안전 여유 1초 뒤
        assertThat(closeService.closeExpired().closed()).isEqualTo(1);
        assertThat(statusOf(id)).isEqualTo("FAILED");
    }

    @Test
    void failedEventGoesToTheOrganizerAndEveryParticipantOnce() {
        seedMembers(4, 6);
        long id = openUnderfilled("이벤트 확인");
        addParticipant(id, 2, 3);
        addParticipant(id, 3, 2);
        clock.set(T.plusSeconds(2));

        closeService.closeExpired();

        GroupBuyFailed e = onlyFailedEvent();
        assertThat(e.groupBuyId()).isEqualTo(id);
        assertThat(e.groupBuyTitle()).isEqualTo("이벤트 확인");
        assertThat(e.eventId()).isNotBlank();
        assertThat(e.recipientIds()).containsExactlyInAnyOrder(ORGANIZER, 2L, 3L).doesNotHaveDuplicates();
    }

    @Test
    void aGroupBuyWithOnlyTheOrganizerFailsWithOneRecipient() {
        long id = openUnderfilled("개설자만");
        clock.set(T.plusSeconds(2));

        closeService.closeExpired();

        assertThat(statusOf(id)).isEqualTo("FAILED");
        assertThat(onlyFailedEvent().recipientIds()).containsExactly(ORGANIZER);
    }

    @Test
    void participationsAreKeptAndTheListShowsFailedWithoutTheOpenChatLink() throws Exception {
        seedMembers(4, 4);
        long id = openUnderfilled("보존 확인");
        addParticipant(id, 2, 3);
        clock.set(T.plusSeconds(2));
        closeService.closeExpired();

        assertThat(participationRows(id)).isEqualTo(2);
        mockMvc.perform(get("/api/groupbuys?status=FAILED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id)).andExpect(jsonPath("$.items[0].status").value("FAILED"));
        mockMvc.perform(get("/api/groupbuys/" + id).with(as(2))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.openChatUrl").value((Object) null));
        mockMvc.perform(get("/api/groupbuys/" + id).with(as(ORGANIZER))).andExpect(jsonPath("$.openChatUrl").value((Object) null));
    }

    @Test
    void oneSummaryLineIsLoggedOnlyWhenThereWereCandidates(CapturedOutput output) {
        closeService.closeExpired();                               // 후보 없음
        assertThat(output.getAll()).doesNotContain("close-run");

        openUnderfilled("로그 확인");
        clock.set(T.plusSeconds(2));
        closeService.closeExpired();

        assertThat(output.getAll()).contains("close-run closed=1 skipped=0 failed=0 rounds=1 durationMs=");
        assertThat(output.getAll().lines().filter(l -> l.contains("close-run")).count()).isEqualTo(1);
    }

    @Test
    void failIsGuardedOnTheEntityToo() {
        var g = new com.camgong.groupbuy.GroupBuy(1L, "t", null, "i", 1200, 24, 24, T, com.camgong.groupbuy.GroupBuyCategory.FOOD,
                "https://a.b", "https://c.d", START);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> g.fail(T)).isInstanceOf(IllegalStateException.class);   // 가득 참
        var open = new com.camgong.groupbuy.GroupBuy(1L, "t", null, "i", 1200, 24, 9, T, com.camgong.groupbuy.GroupBuyCategory.FOOD,
                "https://a.b", "https://c.d", START);
        open.fail(T);
        assertThat(open.getStatus()).isEqualTo(com.camgong.groupbuy.GroupBuyStatus.FAILED);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> open.fail(T)).isInstanceOf(IllegalStateException.class);   // 종료 상태
    }

    @Test
    void theOrganizerIsAlwaysARecipientEvenWithoutAParticipationRow() {
        long id = com.camgong.support.GroupBuySeed.groupBuy().title("개설자 행 없음").deadline(T).quantities(24, 9).insert(jdbc);
        seedMembers(4, 4);
        jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, 4, 9, ?)", id,
                java.time.LocalDateTime.ofInstant(START, java.time.ZoneOffset.UTC));
        clock.set(T.plusSeconds(2));

        closeService.closeExpired();

        assertThat(onlyFailedEvent().recipientIds()).containsExactlyInAnyOrder(ORGANIZER, 4L).doesNotHaveDuplicates();
    }
}

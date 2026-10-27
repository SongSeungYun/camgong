package com.camgong.groupbuy.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 동시 요청이 먼저 같은 상태로 바꿔 내 UPDATE 가 아무것도 바꾸지 못할 때도 응답 본문은 DB 의 현재 상태여야 한다.
 * (스냅샷 격리에서는 일반 SELECT 가 낡은 행을 돌려줄 수 있다 — 잠금 읽기로 다시 읽는다.)
 */
@Import(HeadPause.Config.class)
class CheckStaleResponseIT extends CheckTestBase {

    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @AfterEach
    void cleanUp() {
        HeadPause.disarm();
        pool.shutdownNow();
    }

    @Test
    void aRequestThatLostTheRaceToATrueStillReportsTheCommittedTimestamp() throws Exception {
        long id = completedGroupBuy();
        HeadPause.Pause pause = HeadPause.arm();

        Future<MockHttpServletResponse> losing = pool.submit(() -> pay(ORGANIZER, id, PARTICIPANT, TRUE).andReturn().getResponse());
        pause.awaitReached();                                   // A 는 접근 검사를 마쳤다(스냅샷 고정, paid_at = NULL)
        clock.advance(Duration.ofMinutes(5));
        pay(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk());       // B 가 먼저 입금 확인을 커밋한다
        pause.release();

        MockHttpServletResponse res = losing.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsString()).contains("\"paidConfirmedAt\":\"2026-10-27T09:05:00+09:00\"");
        assertThat(paidAt(id, PARTICIPANT)).isNotNull();
    }

    @Test
    void aRequestThatLostTheRaceToAFalseStillReportsTheClearedState() throws Exception {
        long id = completedGroupBuy();
        pay(ORGANIZER, id, PARTICIPANT, TRUE).andExpect(status().isOk());
        HeadPause.Pause pause = HeadPause.arm();

        Future<MockHttpServletResponse> losing = pool.submit(() -> pay(ORGANIZER, id, PARTICIPANT, FALSE).andReturn().getResponse());
        pause.awaitReached();                                   // A 의 스냅샷에는 paid_at 이 있다
        pay(ORGANIZER, id, PARTICIPANT, FALSE).andExpect(status().isOk()).andExpect(jsonPath("$.paidConfirmedAt").value((Object) null));
        pause.release();

        MockHttpServletResponse res = losing.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsString()).contains("\"paidConfirmedAt\":null");
        assertThat(paidAt(id, PARTICIPANT)).isNull();
    }
}

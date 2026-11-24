package com.camgong.groupbuy.join;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.camgong.groupbuy.close.GroupBuyCloseService;
import com.camgong.support.GroupBuyBodies;

/** 012 T002: 공구가 끝나는 문장마다 ended_at 을 남긴다(보관 기간의 기준). 끝나지 않은 공구는 NULL. 게이트별 하위 클래스가 같은 시험을 돈다. */
class EndedAtIT extends JoinTestBase {

    @Autowired
    GroupBuyCloseService closeService;

    private LocalDateTime endedAt(long id) {
        return jdbc.queryForObject("SELECT ended_at FROM group_buy WHERE id = ?", LocalDateTime.class, id);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    @Test
    void fillingTheLastUnitByJoiningRecordsTheEnd() throws Exception {
        long id = openGroupBuy();
        clock.advance(Duration.ofMinutes(3));
        joinAs(PARTICIPANT, id, 10).andExpect(status().isCreated());
        assertThat(endedAt(id)).as("아직 모집 중").isNull();
        clock.advance(Duration.ofMinutes(2));
        joinAs(OUTSIDER, id, 5).andExpect(status().isCreated());
        assertThat(endedAt(id)).isEqualTo(now());
    }

    @Test
    void loweringTheTotalToTheSecuredQuantityRecordsTheEnd() throws Exception {
        long id = openGroupBuy();
        joinAs(PARTICIPANT, id, 5).andExpect(status().isCreated());
        clock.advance(Duration.ofMinutes(4));
        Map<String, Object> body = GroupBuyBodies.with(GroupBuyBodies.valid(), "totalQuantity", 14);
        updateAs(ORGANIZER, id, body).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM group_buy WHERE id = ?", String.class, id)).isEqualTo("COMPLETED");
        assertThat(endedAt(id)).isEqualTo(now());
    }

    @Test
    void cancellingRecordsTheEnd() throws Exception {
        long id = openGroupBuy();
        clock.advance(Duration.ofMinutes(7));
        cancelAs(ORGANIZER, id).andExpect(status().is2xxSuccessful());
        assertThat(endedAt(id)).isEqualTo(now());
    }

    @Test
    void closingAnUnderfilledGroupBuyRecordsTheEnd() throws Exception {
        long id = openGroupBuy();
        clock.advance(Duration.ofDays(7).plusSeconds(5));
        assertThat(endedAt(id)).as("마감은 지났지만 확정 전(마감 대기)").isNull();
        closeService.closeExpired();
        assertThat(jdbc.queryForObject("SELECT status FROM group_buy WHERE id = ?", String.class, id)).isEqualTo("FAILED");
        assertThat(endedAt(id)).isEqualTo(now());
    }
}

package com.camgong.groupbuy.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.camgong.support.QueryCountInspector;

/** quickstart 시나리오 18: 체크표의 JPA 쿼리 수가 참여자 수와 무관하다(003 의 최종 명단 방식 = DTO 프로젝션을 그대로 쓴다). */
class CheckSheetQueryCountIT extends CheckTestBase {

    @Autowired
    ParticipationCheckService service;

    @Test
    void theNumberOfQueriesDoesNotGrowWithTheNumberOfParticipants() {
        List<Integer> counts = new ArrayList<>();
        for (int n : new int[] { 1, 20, 50 }) {
            jdbc.update("DELETE FROM participation");
            jdbc.update("DELETE FROM group_buy");
            jdbc.update("DELETE FROM member WHERE id >= 100");
            long id = completedGroupBuyWithParticipants(n);

            QueryCountInspector.reset();
            assertThat(service.getCheckSheet(id, ORGANIZER, "all").rows()).hasSize(n);
            counts.add(QueryCountInspector.count());
            System.out.println("[check-sheet queries] participants=" + n + " queries=" + QueryCountInspector.count());
        }
        assertThat(counts).as("참여자 1, 20, 50명").containsOnly(counts.get(0));
        assertThat(counts.get(0)).isLessThanOrEqualTo(3);                         // 공구 요약 1 + 체크표 1 (+ 여유 1)
    }
}

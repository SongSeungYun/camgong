package com.camgong.groupbuy.join;

import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.camgong.support.GroupBuyBodies;

/**
 * 불변식: 모집 완료(COMPLETED) 뒤에는 공구 행과 참여 행의 구성(누가, 몇 개)이 바뀌지 않는다. 006 체크표가 "COMPLETED 는 종료 상태라
 * 명단·상태를 잠금 읽기 없이 읽어도 된다"는 결정에 기대므로, 수정·취소·참여·수량 변경·참여 취소가 모두 거부되고 아무것도 안 바뀐다는 것을
 * 한 곳에서 지킨다.
 */
class CompletedImmutableIT extends JoinTestBase {

    private List<Map<String, Object>> snapshot() {
        return jdbc.queryForList("SELECT 'g' AS k, id, status, secured_quantity AS q, total_quantity AS t, unit_price AS p, "
                + "title AS x, deadline AS d, version AS v FROM group_buy "
                + "UNION ALL SELECT 'p', id, CAST(member_id AS CHAR), quantity, group_buy_id, 0, '', NULL, 0 FROM participation ORDER BY 1, 2");
    }

    @Test
    void everyWritePathIsRejectedAndNothingChangesAfterCompletion() throws Exception {
        long id = groupBuy().status("COMPLETED").quantities(24, 24).price(1200).insert(jdbc);
        for (long[] p : new long[][] { { 1, 9 }, { 2, 6 }, { 3, 9 } }) {
            jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, ?, ?, ?)",
                    id, p[0], p[1], LocalDateTime.ofInstant(START, ZoneOffset.UTC));
        }
        seedMembers(4, 4);
        List<Map<String, Object>> before = snapshot();

        updateAs(1, id, GroupBuyBodies.with(GroupBuyBodies.valid(), "unitPrice", 1500)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("NOT_MODIFIABLE")).andExpect(jsonPath("$.reason").value("COMPLETED"));
        cancelAs(1, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("NOT_MODIFIABLE")).andExpect(jsonPath("$.reason").value("COMPLETED"));
        joinAs(4, id, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("NOT_JOINABLE")).andExpect(jsonPath("$.reason").value("COMPLETED"));                              // 새 참여
        changeAs(2, id, 5).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("NOT_JOINABLE")).andExpect(jsonPath("$.reason").value("COMPLETED"));                            // 수량 변경
        leaveAs(2, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("NOT_JOINABLE")).andExpect(jsonPath("$.reason").value("COMPLETED"));                                // 참여 취소
        leaveAs(3, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("NOT_JOINABLE")).andExpect(jsonPath("$.reason").value("COMPLETED"));

        assertThat(snapshot()).isEqualTo(before);
    }
}

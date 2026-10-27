package com.camgong.groupbuy.join;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.GroupBuyBodies;
import com.camgong.support.IntegrationTestBase;

/** 참여·수량 변경·취소 요청 헬퍼와 불변식 검증 쿼리(data-model.md). */
abstract class JoinTestBase extends IntegrationTestBase {

    protected static Map<String, Object> body(int quantity, int expectedUnitPrice) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("quantity", quantity);
        m.put("expectedUnitPrice", expectedUnitPrice);
        return m;
    }

    protected ResultActions joinAs(long memberId, long groupBuyId, int quantity) throws Exception {
        return joinAs(memberId, groupBuyId, quantity, 1200);
    }

    protected ResultActions joinAs(long memberId, long groupBuyId, int quantity, int expectedUnitPrice) throws Exception {
        return mockMvc.perform(post("/api/groupbuys/" + groupBuyId + "/participations").with(as(memberId))
                .contentType(MediaType.APPLICATION_JSON).content(GroupBuyBodies.json(body(quantity, expectedUnitPrice))));
    }

    protected ResultActions changeAs(long memberId, long groupBuyId, int quantity) throws Exception {
        return changeAs(memberId, groupBuyId, quantity, 1200);
    }

    protected ResultActions changeAs(long memberId, long groupBuyId, int quantity, int expectedUnitPrice) throws Exception {
        return mockMvc.perform(put("/api/groupbuys/" + groupBuyId + "/participations/me").with(as(memberId))
                .contentType(MediaType.APPLICATION_JSON).content(GroupBuyBodies.json(body(quantity, expectedUnitPrice))));
    }

    protected ResultActions leaveAs(long memberId, long groupBuyId) throws Exception {
        return mockMvc.perform(delete("/api/groupbuys/" + groupBuyId + "/participations/me").with(as(memberId)));
    }

    protected int secured(long groupBuyId) {
        return jdbc.queryForObject("SELECT secured_quantity FROM group_buy WHERE id = ?", Integer.class, groupBuyId);
    }

    protected String statusOf(long groupBuyId) {
        return jdbc.queryForObject("SELECT status FROM group_buy WHERE id = ?", String.class, groupBuyId);
    }

    protected int participationRows(long groupBuyId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM participation WHERE group_buy_id = ?", Integer.class, groupBuyId);
    }

    /** I1 위반 공구(secured ≠ Σ quantity). 결과가 비어 있어야 정상이다. */
    protected List<Map<String, Object>> i1Violations() {
        return jdbc.queryForList("SELECT g.id, g.secured_quantity, COALESCE(SUM(p.quantity), 0) AS actual "
                + "FROM group_buy g LEFT JOIN participation p ON p.group_buy_id = g.id "
                + "GROUP BY g.id, g.secured_quantity HAVING g.secured_quantity <> COALESCE(SUM(p.quantity), 0)");
    }

    /** I2 위반(초과). CHECK 때문에 값이 저장되지는 않지만 안전망으로 같이 본다. */
    protected int oversold() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM group_buy WHERE secured_quantity > total_quantity", Integer.class);
    }
}

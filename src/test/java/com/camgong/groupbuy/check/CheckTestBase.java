package com.camgong.groupbuy.check;

import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.IntegrationTestBase;

/** 체크 테스트 공통: 모집 완료 공구(개설자 9 + 참여자 2가 6개 + 참여자 3이 9개, 총 24, 개당 1,200원)와 요청 헬퍼. */
abstract class CheckTestBase extends IntegrationTestBase {

    protected long completedGroupBuy() {
        long id = groupBuy().status("COMPLETED").quantities(24, 24).price(1200).insert(jdbc);
        participate(id, ORGANIZER, 9);
        participate(id, PARTICIPANT, 6);
        participate(id, OUTSIDER, 9);
        return id;
    }

    /** 기본 시드 회원 3명 말고 참여자 n명을 더 넣은 모집 완료 공구(참여자 합이 24 를 넘지 않도록 수량 1, 총 수량은 넉넉히). */
    protected long completedGroupBuyWithParticipants(int n) {
        seedMembers(100, 100 + n);
        long id = groupBuy().status("COMPLETED").quantities(1000, 1 + n).price(1200).insert(jdbc);
        participate(id, ORGANIZER, 1);
        for (int i = 0; i < n; i++) {
            participate(id, 100 + i, 1);
        }
        return id;
    }

    protected void participate(long groupBuyId, long memberId, int quantity) {
        jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, ?, ?, ?)",
                groupBuyId, memberId, quantity, LocalDateTime.ofInstant(START, ZoneOffset.UTC));
    }

    protected ResultActions sheet(long requester, long groupBuyId, String query) throws Exception {
        return mockMvc.perform(get("/api/groupbuys/" + groupBuyId + "/check-sheet" + query).with(as(requester)));
    }

    protected ResultActions pay(long requester, long groupBuyId, long memberId, String body) throws Exception {
        return putCheck("payment", requester, groupBuyId, memberId, body);
    }

    protected ResultActions receive(long requester, long groupBuyId, long memberId, String body) throws Exception {
        return putCheck("receipt", requester, groupBuyId, memberId, body);
    }

    private ResultActions putCheck(String kind, long requester, long groupBuyId, long memberId, String body) throws Exception {
        return mockMvc.perform(put("/api/groupbuys/" + groupBuyId + "/participations/" + memberId + "/" + kind)
                .with(as(requester)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected static final String TRUE = "{\"confirmed\":true}";
    protected static final String FALSE = "{\"confirmed\":false}";

    protected java.time.LocalDateTime paidAt(long groupBuyId, long memberId) {
        return jdbc.queryForObject("SELECT paid_at FROM participation WHERE group_buy_id = ? AND member_id = ?",
                java.time.LocalDateTime.class, groupBuyId, memberId);
    }

    protected java.time.LocalDateTime receivedAt(long groupBuyId, long memberId) {
        return jdbc.queryForObject("SELECT received_at FROM participation WHERE group_buy_id = ? AND member_id = ?",
                java.time.LocalDateTime.class, groupBuyId, memberId);
    }
}

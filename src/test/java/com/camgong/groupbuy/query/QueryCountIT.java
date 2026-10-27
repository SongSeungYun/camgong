package com.camgong.groupbuy.query;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.camgong.support.IntegrationTestBase;
import com.camgong.support.QueryCountInspector;

/**
 * N+1 쿼리 수 측정(헌법 II). 시나리오 B: 상세의 개설자용 명단, A: 내가 참여한 공구 한 페이지.
 * 순진한 구현 시점에는 측정값만 출력하고, 개선을 마친 뒤 상한 단언을 건다(T030).
 */
class QueryCountIT extends IntegrationTestBase {

    // 요청 전체가 아니라 조회 서비스가 쓰는 쿼리만 센다(인증 필터의 회원 조회 1건은 상수라 제외).
    @org.springframework.beans.factory.annotation.Autowired
    GroupBuyQueryService service;

    private long groupBuyWithParticipants(int n) {
        long id = com.camgong.support.GroupBuySeed.groupBuy().quantities(1000, 1).insert(jdbc);
        jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, 1, 1, ?)", id,
                LocalDateTime.ofInstant(START, ZoneOffset.UTC));
        for (int i = 0; i < n - 1; i++) {
            long member = 100 + i;
            jdbc.update("INSERT INTO member (id, name, display_name_key, email, email_key, password_hash, consent_at, "
                    + "consent_version, created_at) VALUES (?, ?, ?, ?, ?, 'x', ?, 'v1', ?)", member, "참여" + member,
                    "참여" + member, "p" + member + "@school.ac.kr", "p" + member + "@school.ac.kr",
                    LocalDateTime.ofInstant(START, ZoneOffset.UTC), LocalDateTime.ofInstant(START, ZoneOffset.UTC));
            jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, ?, 1, ?)",
                    id, member, LocalDateTime.ofInstant(START, ZoneOffset.UTC));
        }
        return id;
    }

    @Test
    void scenarioB_detailRosterForOrganizer() throws Exception {
        for (int n : new int[] { 1, 20, 50 }) {
            jdbc.update("DELETE FROM participation");
            jdbc.update("DELETE FROM group_buy");
            jdbc.update("DELETE FROM member WHERE id >= 100");
            long id = groupBuyWithParticipants(n);
            QueryCountInspector.reset();
            service.detail(ORGANIZER, id);
            System.out.println("[N+1 B] participants=" + n + " queries=" + QueryCountInspector.count());
        }
    }

    @Test
    void scenarioA_myParticipatedGroupBuysOnePage() throws Exception {
        for (int n : new int[] { 1, 20, 50 }) {
            jdbc.update("DELETE FROM participation");
            jdbc.update("DELETE FROM group_buy");
            for (int i = 0; i < n; i++) {
                long id = com.camgong.support.GroupBuySeed.groupBuy().organizer(PARTICIPANT).quantities(1000, 1).insert(jdbc);
                jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, ?, 1, ?)",
                        id, ORGANIZER, LocalDateTime.ofInstant(START, ZoneOffset.UTC));
            }
            QueryCountInspector.reset();
            service.mine(ORGANIZER, "participant", null, "50");
            System.out.println("[N+1 A] items=" + n + " queries=" + QueryCountInspector.count());
        }
    }
}

package com.camgong.groupbuy.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.camgong.support.IntegrationTestBase;
import com.camgong.support.QueryCountInspector;

/**
 * N+1 회귀 테스트(헌법 II). 시나리오 B: 상세의 개설자용 명단, A: 내가 참여한 공구 한 페이지.
 * 쿼리 수가 데이터 양(N)과 무관해야 한다. 단계별 측정값은 docs/benchmarks/browse-n-plus-one.md.
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
            assertThat(QueryCountInspector.count()).as("개설자 상세, 참여자 %d명", n).isLessThanOrEqualTo(4);

            QueryCountInspector.reset();
            service.detail(PARTICIPANT, id);
            assertThat(QueryCountInspector.count()).as("참여자 상세, 참여자 %d명", n).isLessThanOrEqualTo(3);
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
            assertThat(QueryCountInspector.count()).as("내가 참여한 공구 %d건", n).isLessThanOrEqualTo(2);
        }
    }
}

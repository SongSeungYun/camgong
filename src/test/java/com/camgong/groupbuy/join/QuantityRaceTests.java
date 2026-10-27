package com.camgong.groupbuy.join;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

import com.camgong.support.Concurrent;

/**
 * quickstart 시나리오 16, 17, 18: 마지막 자리 경쟁에서 확보 수량이 총 수량을 넘지 않고 모집 완료 전환이 한 번이다.
 * 구현체(camgong.quantity-gate)마다 컨텍스트가 달라서 방식별 서브클래스가 이 테스트를 그대로 상속해 돌린다.
 * 반복 횟수는 시스템 속성 race.repeat 로 늘린다(CI 기본 20, 수동 1,000).
 */
abstract class QuantityRaceTests extends JoinTestBase {

    private static final int REPEAT = Integer.getInteger("race.repeat", 20);

    /** 한 요청의 결과: 상태 코드와, 오류면 title. */
    private record Outcome(int status, String title) {
        @Override
        public String toString() {
            return status + (title == null ? "" : ":" + title);
        }
    }

    private Outcome join(long memberId, long groupBuyId, int quantity) throws Exception {
        var res = joinAs(memberId, groupBuyId, quantity).andReturn().getResponse();
        String title = null;
        if (res.getStatus() >= 400) {
            title = com.jayway.jsonpath.JsonPath.read(res.getContentAsString(), "$.title");
        }
        return new Outcome(res.getStatus(), title);
    }

    private void assertInvariants(long id) {
        assertThat(i1Violations()).as("I1 secured = Σ quantity").isEmpty();
        assertThat(oversold()).as("I2 secured <= total").isZero();
        assertThat(secured(id)).isLessThanOrEqualTo(24);
    }

    @Test
    void lastSeatGoesToExactlyOneOfOneHundred() throws Exception {
        seedMembers(4, 101);
        for (int round = 0; round < REPEAT; round++) {
            events.clear();
            long id = openGroupBuy(com.camgong.support.GroupBuyBodies.with(com.camgong.support.GroupBuyBodies.valid(),
                    "title", "마지막 자리 " + round));
            jdbc.update("UPDATE group_buy SET secured_quantity = 23 WHERE id = ?", id);   // 남은 1
            jdbc.update("UPDATE participation SET quantity = 23 WHERE group_buy_id = ? AND member_id = 1", id);

            List<Callable<Outcome>> calls = new ArrayList<>();
            for (long member = 2; member <= 101; member++) {
                long m = member;
                calls.add(() -> join(m, id, 1));
            }
            List<Outcome> outcomes = Concurrent.all(calls);

            assertThat(outcomes).as("round %d", round).filteredOn(o -> o.status() == 201).hasSize(1);
            assertThat(outcomes).filteredOn(o -> o.status() != 201)
                    .allMatch(o -> o.status() == 409 && (o.title().equals("NOT_JOINABLE")
                            || o.title().equals("INSUFFICIENT_QUANTITY") || o.title().equals("BUSY")),
                            "거부는 모두 409 (NOT_JOINABLE / INSUFFICIENT_QUANTITY / BUSY)");
            assertThat(statusOf(id)).isEqualTo("COMPLETED");
            assertThat(secured(id)).isEqualTo(24);
            assertThat(events.completed()).as("완료 이벤트는 한 번").hasSize(1);
            assertInvariants(id);
        }
    }

    @Test
    void mixedQuantitiesNeverExceedWhatWasLeft() throws Exception {
        seedMembers(4, 20);
        for (int round = 0; round < REPEAT; round++) {
            long id = openGroupBuy(com.camgong.support.GroupBuyBodies.with(com.camgong.support.GroupBuyBodies.valid(),
                    "title", "다양한 수량 " + round));
            // 총 24, 개설자 9 → 남은 15. 남은 10 으로 만들기 위해 회원 2 가 5 를 먼저 잡는다.
            joinAs(2, id, 5).andReturn();
            assertThat(secured(id)).isEqualTo(14);

            int[] quantities = { 3, 4, 5, 6, 3, 4, 5, 6 };
            List<Callable<Outcome>> calls = new ArrayList<>();
            for (int i = 0; i < quantities.length; i++) {
                long member = 3 + i;
                int q = quantities[i];
                calls.add(() -> join(member, id, q));
            }
            List<Outcome> outcomes = Concurrent.all(calls);

            int accepted = 0;
            for (int i = 0; i < quantities.length; i++) {
                if (outcomes.get(i).status() == 201) {
                    accepted += quantities[i];
                }
            }
            assertThat(accepted).as("round %d 성공 수량 합", round).isBetween(1, 10);   // 전부 거부하는 구현도 걸러낸다
            // 거부는 "남은 수량 부족"이거나 완료 뒤의 NOT_JOINABLE 뿐이다(낙관적 방식만 BUSY 가 섞일 수 있다). 남은 수량은 줄기만 하므로
            // 부족으로 거부된 요청의 수량은 최종 남은 수량보다 커야 한다 — 들어갈 자리가 있는데 거부한 경우(과소 수용)를 잡는다.
            int remaining = 24 - secured(id);
            for (int i = 0; i < quantities.length; i++) {
                Outcome o = outcomes.get(i);
                if (o.status() == 201) {
                    continue;
                }
                assertThat(o.status()).as("round %d 요청 %d", round, i).isEqualTo(409);
                assertThat(o.title()).isIn("INSUFFICIENT_QUANTITY", "NOT_JOINABLE", "BUSY");
                if (o.title().equals("INSUFFICIENT_QUANTITY")) {
                    assertThat(quantities[i]).as("round %d 요청 %d 수량", round, i).isGreaterThan(remaining);
                }
            }
            assertThat(secured(id)).isEqualTo(14 + accepted);
            assertInvariants(id);
        }
    }

    @Test
    void sameMemberSendingTheSameRequestTwiceAtOnceCreatesOneRow() throws Exception {
        for (int round = 0; round < REPEAT; round++) {
            long id = openGroupBuy(com.camgong.support.GroupBuyBodies.with(com.camgong.support.GroupBuyBodies.valid(),
                    "title", "이중 요청 " + round));
            Overlap.arm(2);
            List<Outcome> outcomes;
            try {
                outcomes = Concurrent.all(List.of(() -> join(2, id, 4), () -> join(2, id, 4)));
            } finally {
                Overlap.disarm();
            }

            assertThat(outcomes).as("round %d", round).extracting(Outcome::status).containsExactlyInAnyOrder(200, 201);
            assertThat(participationRows(id)).isEqualTo(2);          // 개설자 + 회원 2
            assertThat(secured(id)).isEqualTo(13);
            assertInvariants(id);
        }
    }

    @Test
    void everyWriteBumpsTheVersionSoGatesCanBeMixed() throws Exception {
        long id = openGroupBuy();
        seedMembers(4, 6);
        long v0 = version(id);
        joinAs(2, id, 2).andReturn();
        long v1 = version(id);
        changeAs(2, id, 3).andReturn();
        long v2 = version(id);
        leaveAs(2, id).andReturn();
        long v3 = version(id);
        assertThat(List.of(v0, v1, v2, v3)).isSorted().doesNotHaveDuplicates();
    }

    private long version(long id) {
        return jdbc.queryForObject("SELECT version FROM group_buy WHERE id = ?", Long.class, id);
    }

    @Test
    void joinsChangesAndLeavesTogetherKeepTheSumsConsistent() throws Exception {
        seedMembers(4, 40);
        for (int round = 0; round < Math.max(5, REPEAT / 2); round++) {
            long id = openGroupBuy(com.camgong.support.GroupBuyBodies.with(com.camgong.support.GroupBuyBodies.valid(),
                    "title", "섞인 쓰기 " + round));
            // 회원 2~11 이 1개씩 참여해 둔 상태(확보 19, 남은 5)에서 일부는 취소·변경, 새 회원은 참여
            for (long m = 2; m <= 11; m++) {
                joinAs(m, id, 1).andReturn();
            }
            List<Callable<Outcome>> calls = new ArrayList<>();
            for (long m = 2; m <= 5; m++) {
                long member = m;
                calls.add(() -> {
                    var res = leaveAs(member, id).andReturn().getResponse();
                    return new Outcome(res.getStatus(), null);
                });
            }
            for (long m = 6; m <= 11; m++) {
                long member = m;
                calls.add(() -> {
                    var res = changeAs(member, id, 2).andReturn().getResponse();
                    return new Outcome(res.getStatus(), null);
                });
            }
            for (long m = 12; m <= 20; m++) {
                long member = m;
                calls.add(() -> join(member, id, 2));
            }
            List<Outcome> outcomes = Concurrent.all(calls);

            assertThat(outcomes).as("round %d", round).extracting(Outcome::status).allMatch(st -> st == 200 || st == 201
                    || st == 204 || st == 409, "허용된 응답만");
            assertInvariants(id);
        }
    }
}

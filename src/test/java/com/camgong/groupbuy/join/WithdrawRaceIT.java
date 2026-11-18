package com.camgong.groupbuy.join;

import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.camgong.support.Concurrent;
import com.camgong.support.GroupBuyBodies;
import com.camgong.support.IntegrationTestBase;

/**
 * 010 T008: 탈퇴와 그 회원의 새 참여·새 개설을 동시에 보내도 "탈퇴했는데 모집 중 공구에 남음"이 생기지 않는다.
 * 반복마다 새 회원을 만들어 같은 순간에 출발시킨다. 둘 중 하나는 거부되어야 한다.
 */
class WithdrawRaceIT extends IntegrationTestBase {

    private static final int REPEAT = Integer.getInteger("race.repeat", 20) * 5;   // 기본 100회

    @Autowired
    DataSource dataSource;

    /** 탈퇴 쪽 BCrypt 가 길어 그냥 동시에 보내면 참여·개설이 늘 이긴다. 상대 쪽을 0~11ms 늦춰 두 순서가 다 나오게 한다. */
    private static void jitter() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(12));
    }

    private int withdraw(long member) throws Exception {
        return mockMvc.perform(post("/api/auth/withdraw").with(as(member)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"password1\"}")).andReturn().getResponse().getStatus();
    }

    private int join(long member, long groupBuyId) throws Exception {
        return mockMvc.perform(post("/api/groupbuys/" + groupBuyId + "/participations").with(as(member))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1,\"expectedUnitPrice\":1200}"))
                .andReturn().getResponse().getStatus();
    }

    private int open(long member, int n) throws Exception {
        Map<String, Object> body = GroupBuyBodies.with(GroupBuyBodies.valid(), "title", "경쟁 " + n);
        return mockMvc.perform(post("/api/groupbuys").with(as(member)).contentType(MediaType.APPLICATION_JSON)
                .content(GroupBuyBodies.json(body))).andReturn().getResponse().getStatus();
    }

    /** 탈퇴한 회원이 개설자나 참여자로 있는 OPEN 공구 수. 0 이어야 한다. */
    private int withdrawnButActive() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM member m WHERE m.withdrawn_at IS NOT NULL AND (
                    EXISTS (SELECT 1 FROM group_buy g WHERE g.organizer_id = m.id AND g.status = 'OPEN')
                    OR EXISTS (SELECT 1 FROM participation p JOIN group_buy g ON g.id = p.group_buy_id
                               WHERE p.member_id = m.id AND g.status = 'OPEN'))""", Integer.class);
    }

    @Test
    void withdrawRacingANewJoinNeverLeavesAWithdrawnParticipant() throws Exception {
        long groupBuyId = groupBuy().quantities(100_000, 1).insert(jdbc);
        List<String> outcomes = new ArrayList<>();
        for (int i = 0; i < REPEAT; i++) {
            long member = 1000 + i;
            seedLoginMember(member, "r" + member + "@school.ac.kr", "경쟁" + member, "password1");
            List<Callable<Integer>> calls = List.of(() -> withdraw(member), () -> {
                jitter();
                return join(member, groupBuyId);
            });
            List<Integer> s = Concurrent.all(calls);
            outcomes.add(s.get(0) + "/" + s.get(1));
            // 탈퇴가 이기면 참여는 401, 참여가 이기면 탈퇴는 409. 둘 다 성공하거나 500 이 나면 안 된다.
            assertThat(s).as("반복 %d", i).isIn(List.of(204, 401), List.of(409, 201));
        }
        assertThat(withdrawnButActive()).isZero();
        System.out.println("withdraw/join outcomes: " + outcomes.stream().distinct().map(o -> o + "=" +
                outcomes.stream().filter(o::equals).count()).toList());
    }

    @Test
    void withdrawRacingANewGroupBuyNeverLeavesAWithdrawnOrganizer() throws Exception {
        List<String> outcomes = new ArrayList<>();
        for (int i = 0; i < REPEAT; i++) {
            long member = 2000 + i;
            int n = i;
            seedLoginMember(member, "o" + member + "@school.ac.kr", "개설경쟁" + member, "password1");
            List<Callable<Integer>> calls = List.of(() -> withdraw(member), () -> {
                jitter();
                return open(member, n);
            });
            List<Integer> s = Concurrent.all(calls);
            outcomes.add(s.get(0) + "/" + s.get(1));
            assertThat(s).as("반복 %d", i).isIn(List.of(204, 401), List.of(409, 201));
        }
        assertThat(withdrawnButActive()).isZero();
        System.out.println("withdraw/open outcomes: " + outcomes.stream().distinct().map(o -> o + "=" +
                outcomes.stream().filter(o::equals).count()).toList());
    }

    /** 탈퇴가 회원 행을 잡고 있는 동안 온 새 참여는 기다렸다가, 탈퇴가 커밋되면 401 이다(참여 쪽 FOR SHARE). */
    @Test
    void aJoinArrivingWhileAWithdrawalHoldsTheMemberRowWaitsAndIsRejected() throws Exception {
        long groupBuyId = groupBuy().quantities(100, 9).insert(jdbc);
        seedLoginMember(30, "x30@school.ac.kr", "기다림", "password1");
        try (var c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            c.createStatement().executeQuery("SELECT id FROM member WHERE id = 30 FOR UPDATE").close();
            c.createStatement().executeUpdate("UPDATE member SET withdrawn_at = NOW(6) WHERE id = 30");
            CompletableFuture<Integer> joining = CompletableFuture.supplyAsync(() -> {
                try {
                    return join(30, groupBuyId);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            awaitLockWait(5000);
            c.commit();
            assertThat(joining.get()).isEqualTo(401);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participation WHERE member_id = 30", Integer.class)).isZero();
    }

    /** 새 참여가 회원 행을 공유 잠금으로 잡고 참여 행을 넣는 동안 온 탈퇴는 기다렸다가, 그 참여를 보고 409 다(잠금 뒤 스냅샷). */
    @Test
    void aWithdrawalArrivingWhileAJoinHoldsTheMemberRowSeesTheJoin() throws Exception {
        long groupBuyId = groupBuy().quantities(100, 9).insert(jdbc);
        seedLoginMember(31, "x31@school.ac.kr", "참여중", "password1");
        try (var c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            c.createStatement().executeQuery("SELECT withdrawn_at FROM member WHERE id = 31 FOR SHARE").close();
            c.createStatement().executeUpdate("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) "
                    + "VALUES (" + groupBuyId + ", 31, 1, NOW(6))");
            CompletableFuture<Integer> withdrawing = CompletableFuture.supplyAsync(() -> {
                try {
                    return withdraw(31);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            awaitLockWait(5000);
            c.commit();
            assertThat(withdrawing.get()).isEqualTo(409);
        }
        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NULL FROM member WHERE id = 31", Boolean.class)).isTrue();
    }
}

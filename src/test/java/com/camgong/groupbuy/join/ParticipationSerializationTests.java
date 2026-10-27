package com.camgong.groupbuy.join;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.camgong.support.Concurrent;
import com.camgong.support.GroupBuyBodies;

/**
 * quickstart 시나리오 19 와 개설자 수정·취소와 참여가 동시에 올 때: 먼저 처리된 쪽이 기준이 되고 I1·I2 가 유지된다.
 * 방식별 서브클래스가 상속해 돌린다.
 */
abstract class ParticipationSerializationTests extends JoinTestBase {

    private static final int REPEAT = Integer.getInteger("race.repeat", 20);

    @Test
    void organizerCancelRacingAJoinLeavesNoParticipationOnACancelledGroupBuyAfterTheCancel() throws Exception {
        for (int round = 0; round < REPEAT; round++) {
            long id = openGroupBuy(GroupBuyBodies.with(GroupBuyBodies.valid(), "title", "취소 경쟁 " + round));

            List<Integer> statuses = Concurrent.all(List.of(
                    () -> joinAs(2, id, 3).andReturn().getResponse().getStatus(),
                    () -> cancelAs(ORGANIZER, id).andReturn().getResponse().getStatus()));

            int join = statuses.get(0);
            assertThat(statuses.get(1)).as("취소는 항상 성공 (round %d)", round).isEqualTo(204);
            assertThat(statusOf(id)).isEqualTo("CANCELLED");
            // 참여가 취소보다 먼저 처리됐을 때만 참여 행이 있다. 취소 뒤의 참여는 404 이고 행이 없다.
            assertThat(join).isIn(201, 404);
            assertThat(participationRows(id)).isEqualTo(join == 201 ? 2 : 1);
            assertThat(i1Violations()).isEmpty();
        }
    }

    @Test
    void organizerUpdateRacingAJoinNeverBreaksTheInvariants() throws Exception {
        for (int round = 0; round < REPEAT; round++) {
            long id = openGroupBuy(GroupBuyBodies.with(GroupBuyBodies.valid(), "title", "수정 경쟁 " + round));
            // 총 24, 개설자 9. 개설자가 14 로 올리는 수정과 12 개 참여는 함께 성립할 수 없다(14 + 12 > 24).
            var update = GroupBuyBodies.with(GroupBuyBodies.with(GroupBuyBodies.valid(), "title", "수정 경쟁 " + round),
                    "organizerQuantity", 14);

            List<Integer> statuses = Concurrent.all(List.of(
                    () -> joinAs(2, id, 12).andReturn().getResponse().getStatus(),
                    () -> updateAs(ORGANIZER, id, update).andReturn().getResponse().getStatus()));

            int join = statuses.get(0);
            int upd = statuses.get(1);
            boolean joinFirst = join == 201 && upd == 400;
            boolean updateFirst = join == 409 && upd == 200;
            assertThat(joinFirst || updateFirst).as("round %d: 둘 중 정확히 하나만 성공 (join=%d, update=%d)", round, join, upd)
                    .isTrue();
            assertThat(secured(id)).isLessThanOrEqualTo(24);
            assertThat(i1Violations()).isEmpty();
            assertThat(oversold()).isZero();
        }
    }
}

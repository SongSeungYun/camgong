package com.camgong.groupbuy.join;

import java.time.Instant;

import com.camgong.groupbuy.GroupBuy;
import com.camgong.groupbuy.GroupBuyRepository;
import com.camgong.groupbuy.GroupBuyStatus;

/**
 * 공구 행을 SELECT ... FOR UPDATE 로 잡고(research 결정 4) 최신 값으로 검사한 뒤 갱신한다. 같은 공구의 요청은 락으로 직렬화된다.
 * 검사 순서: 없음·취소 → 모집 중 아님·마감 → 가격 → 남은 수량.
 */
class PessimisticQuantityGate implements QuantityGate {

    private final GroupBuyRepository groupBuys;
    private final java.time.Clock clock;

    PessimisticQuantityGate(GroupBuyRepository groupBuys, java.time.Clock clock) {
        this.groupBuys = groupBuys;
        this.clock = clock;
    }

    @Override
    public GateResult reserve(long groupBuyId, int delta, Integer expectedUnitPrice, Instant requestedAt) {
        GroupBuy g = groupBuys.findByIdForUpdate(groupBuyId).orElse(null);
        // 마감 판정은 락을 받은 뒤의 시각으로 한다(spec FR-009). 락을 기다리는 동안 마감이 지났다면 도착 시각(requestedAt)이 아니라
        // 지금 기준으로 거부한다. 도착 시각 이후에 시계가 거꾸로 가는 일은 없으므로 더 늦은 쪽을 쓴다.
        Instant now = Instant.now(clock);
        if (now.isBefore(requestedAt)) {
            now = requestedAt;
        }
        if (g == null || g.getStatus() == GroupBuyStatus.CANCELLED) {
            return GateResult.notFound();
        }
        String notJoinable = notJoinableReason(g, now);
        if (notJoinable != null) {
            return GateResult.notJoinable(notJoinable);
        }
        if (expectedUnitPrice != null && expectedUnitPrice != g.getUnitPrice()) {
            return GateResult.priceChanged(g.getUnitPrice());
        }
        // secured + delta 로 쓰면 delta 가 아주 클 때(Integer.MAX_VALUE) int 가 넘쳐 음수가 되어 한도 안으로 오판한다. 뺄셈으로 비교한다.
        if (delta > g.getTotalQuantity() - g.getSecuredQuantity()) {
            return GateResult.insufficient(g.getSecuredQuantity(), g.getTotalQuantity());
        }
        g.reserve(delta, now);
        boolean completed = g.completeIfFull();
        return GateResult.reserved(g.getSecuredQuantity(), g.getTotalQuantity(), g.getUnitPrice(), completed);
    }

    /** OPEN 이 아니거나 마감 시각 ≤ 지금이면 사유를, 참여 가능하면 null. */
    static String notJoinableReason(GroupBuy g, Instant now) {
        return switch (g.getStatus()) {
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
            case OPEN -> g.getDeadline().isAfter(now) ? null : "DEADLINE_PASSED";
            case CANCELLED -> throw new IllegalStateException("취소된 공구는 호출 전에 걸러진다");
        };
    }
}

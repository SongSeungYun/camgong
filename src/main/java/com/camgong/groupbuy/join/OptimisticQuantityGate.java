package com.camgong.groupbuy.join;

import java.time.Instant;

import com.camgong.groupbuy.GroupBuy;
import com.camgong.groupbuy.GroupBuyRepository;
import com.camgong.groupbuy.GroupBuyStatus;

/**
 * 락 없이 읽고 검사한 뒤 @Version 이 붙은 UPDATE(WHERE version = ?)로 갱신한다. 그 사이 다른 요청이 같은 행을 바꿨으면
 * 0행이 되어 낙관적 락 예외가 난다. 호출한 서비스가 새 트랜잭션에서 최대 3번 다시 시도하고 소진하면 BUSY 로 답한다
 * (같은 트랜잭션 안의 재시도는 REPEATABLE READ 스냅샷이 그대로라 의미가 없다).
 * 모든 쓰기가 version 을 올리는 규칙을 따른다(직접 UPDATE 인 조건부 방식과 맞물리도록).
 */
class OptimisticQuantityGate implements QuantityGate {

    private final GroupBuyRepository groupBuys;

    OptimisticQuantityGate(GroupBuyRepository groupBuys) {
        this.groupBuys = groupBuys;
    }

    @Override
    public GateResult reserve(long groupBuyId, int delta, Integer expectedUnitPrice, Instant now) {
        GroupBuy g = groupBuys.findById(groupBuyId).orElse(null);
        if (g == null || g.getStatus() == GroupBuyStatus.CANCELLED) {
            return GateResult.notFound();
        }
        String notJoinable = PessimisticQuantityGate.notJoinableReason(g, now);
        if (notJoinable != null) {
            return GateResult.notJoinable(notJoinable);
        }
        if (expectedUnitPrice != null && expectedUnitPrice != g.getUnitPrice()) {
            return GateResult.priceChanged(g.getUnitPrice());
        }
        if (g.getSecuredQuantity() + delta > g.getTotalQuantity()) {
            return GateResult.insufficient(g.getSecuredQuantity(), g.getTotalQuantity());
        }
        g.reserve(delta, now);
        boolean completed = g.completeIfFull();
        groupBuys.saveAndFlush(g);          // 여기서 version 충돌이 드러난다(ObjectOptimisticLockingFailureException)
        return GateResult.reserved(g.getSecuredQuantity(), g.getTotalQuantity(), g.getUnitPrice(), completed);
    }
}

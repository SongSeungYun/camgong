package com.camgong.groupbuy.join;

import java.time.Instant;

/**
 * 확보 수량을 늘리거나 줄이는 관문. group_buy 행(상태·마감·가격·수량)만 책임지고, 참여 행과 이벤트는 건드리지 않는다.
 * 호출자의 트랜잭션 안에서 실행된다. 구현체 4개(비관적, 조건부 UPDATE, 낙관적, 락 없음)는 같은 계약을 다른 동시성 전략으로 지킨다.
 */
public interface QuantityGate {

    /**
     * @param delta 늘릴 양(참여 +q, 수량 변경 새−기존, 취소 −기존)
     * @param expectedUnitPrice 사용자가 본 개당 가격. 취소는 null(가격을 보지 않는다)
     */
    GateResult reserve(long groupBuyId, int delta, Integer expectedUnitPrice, Instant now);
}

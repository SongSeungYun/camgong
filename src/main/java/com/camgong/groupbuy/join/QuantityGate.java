package com.camgong.groupbuy.join;

import java.time.Instant;

/**
 * 확보 수량을 늘리거나 줄이는 관문. group_buy 행(상태·마감·가격·수량)만 책임지고, 참여 행과 이벤트는 건드리지 않는다.
 * 호출자의 트랜잭션 안에서 실행된다. 구현체 4개(비관적, 조건부 UPDATE, 낙관적, 락 없음)는 같은 계약을 다른 동시성 전략으로 지킨다.
 */
public interface QuantityGate {

    /**
     * 마감 판정에 쓰는 시각의 기준이 구현마다 다르다(spec FR-009, 처리하는 순간의 시각):
     * <ul>
     * <li>pessimistic(기본): 공구 행 락을 **받은 뒤** 시계를 다시 읽어 판정한다. 락을 기다리는 동안 마감이 지났어도 지금 기준으로 거부한다.</li>
     * <li>conditional · optimistic · none: 시도를 시작할 때의 시각({@code now})으로 판정한다. 오차는 그 시도가 락을 기다린 시간이고
     * 세션 락 대기 시간(5초) 이내다. 마감 확정과 겹치는 경합은 안전하다(확정이 먼저면 조건부 UPDATE 가 0행, 낙관적은 버전 충돌).
     * 남는 위험은 다른 참여·수정이 쥔 락을 기다리는 경우뿐이며, 그때도 확보 수량·모집 완료 불변식은 깨지지 않는다.</li>
     * </ul>
     *
     * @param delta 늘릴 양(참여 +q, 수량 변경 새−기존, 취소 −기존)
     * @param expectedUnitPrice 사용자가 본 개당 가격. 취소는 null(가격을 보지 않는다)
     */
    GateResult reserve(long groupBuyId, int delta, Integer expectedUnitPrice, Instant now);
}

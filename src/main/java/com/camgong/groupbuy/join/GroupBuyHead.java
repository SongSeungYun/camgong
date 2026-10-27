package com.camgong.groupbuy.join;

import com.camgong.groupbuy.GroupBuyStatus;

/**
 * 공구 행의 요약(엔티티가 아닌 값). 락을 잡기 전의 사전 검사에 쓴다. 엔티티로 읽으면 영속성 컨텍스트에 낡은 상태가 남아
 * 이어지는 FOR UPDATE 조회가 최신 값으로 갱신되지 않으므로 값만 읽는다.
 */
public record GroupBuyHead(Long organizerId, GroupBuyStatus status, int unitPrice, int securedQuantity,
        int totalQuantity, java.time.Instant deadline) {
}

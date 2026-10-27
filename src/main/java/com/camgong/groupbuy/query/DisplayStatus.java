package com.camgong.groupbuy.query;

import java.time.Instant;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import com.camgong.groupbuy.GroupBuy;
import com.camgong.groupbuy.GroupBuyStatus;

/**
 * 저장 상태와 마감 시각에서 서버가 계산하는 표시 상태(research 결정 5). 프론트가 마감 판정을 따로 구현하지 않는다.
 * CANCELLED 는 어디에도 노출하지 않는다.
 */
public enum DisplayStatus {
    OPEN, PENDING_CLOSE, COMPLETED, FAILED;

    public static DisplayStatus derive(GroupBuyStatus status, Instant deadline, Instant now) {
        return switch (status) {
            case OPEN -> deadline.isAfter(now) ? OPEN : PENDING_CLOSE;
            case COMPLETED -> COMPLETED;
            case FAILED -> FAILED;
            case CANCELLED -> throw new IllegalArgumentException("CANCELLED 는 표시 상태가 없다");
        };
    }

    /** derive 와 같은 조건을 쿼리 필터로 만든다. 두 곳이 어긋나지 않도록 ListGroupBuyIT 가 상태별로 대조한다. */
    public Predicate toPredicate(CriteriaBuilder cb, Root<GroupBuy> g, Instant now) {
        return switch (this) {
            case OPEN -> cb.and(cb.equal(g.get("status"), GroupBuyStatus.OPEN), cb.greaterThan(g.<Instant>get("deadline"), now));
            case PENDING_CLOSE ->
                cb.and(cb.equal(g.get("status"), GroupBuyStatus.OPEN), cb.lessThanOrEqualTo(g.<Instant>get("deadline"), now));
            case COMPLETED -> cb.equal(g.get("status"), GroupBuyStatus.COMPLETED);
            case FAILED -> cb.equal(g.get("status"), GroupBuyStatus.FAILED);
        };
    }
}

package com.camgong.groupbuy.join;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.camgong.groupbuy.GroupBuy;

/** 참여 서비스의 사전 검사용 값 조회. 일반 SELECT(락 없음)라 REPEATABLE READ 스냅샷을 본다 — 판정은 게이트가 락 안에서 다시 한다. */
public interface JoinQueryRepository extends Repository<GroupBuy, Long> {

    @Query("select new com.camgong.groupbuy.join.GroupBuyHead(g.organizerId, g.status, g.unitPrice, g.securedQuantity, "
            + "g.totalQuantity) from GroupBuy g where g.id = :id")
    Optional<GroupBuyHead> findHead(@Param("id") Long id);

    @Query("select p.quantity from Participation p where p.groupBuyId = :groupBuyId and p.memberId = :memberId")
    Optional<Integer> findQuantity(@Param("groupBuyId") Long groupBuyId, @Param("memberId") Long memberId);
}

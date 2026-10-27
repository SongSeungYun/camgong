package com.camgong.groupbuy.check;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.camgong.groupbuy.Participation;

/**
 * 체크표 조회. 003 의 개설자 명단과 같은 방식(필요한 컬럼만 조인하는 DTO 프로젝션, 지연 로딩 없음)에 확인 시각 두 컬럼을 더했다.
 * 잠금 읽기가 필요 없다: 체크는 COMPLETED 공구에서만 하고 COMPLETED 는 종료 상태라 공구·참여 행이 이후 바뀌지 않으며 락 대기도 없어서
 * REPEATABLE READ 스냅샷이 낡을 일이 없다(004·005 의 잠금 읽기 규칙과 다른 점).
 */
public interface CheckQueryRepository extends Repository<Participation, Long> {

    record Raw(Long memberId, String name, int quantity, Instant paidAt, Instant receivedAt) {
    }

    @Query("select new com.camgong.groupbuy.check.CheckQueryRepository$Raw(p.memberId, m.name, p.quantity, p.paidAt, p.receivedAt) "
            + "from Participation p join p.member m where p.groupBuyId = :groupBuyId and p.memberId <> :organizerId "
            + "order by p.id")
    List<Raw> findAll(@Param("groupBuyId") Long groupBuyId, @Param("organizerId") Long organizerId);

    @Query("select new com.camgong.groupbuy.check.CheckQueryRepository$Raw(p.memberId, m.name, p.quantity, p.paidAt, p.receivedAt) "
            + "from Participation p join p.member m where p.groupBuyId = :groupBuyId and p.memberId <> :organizerId "
            + "and p.paidAt is null order by p.id")
    List<Raw> findUnpaid(@Param("groupBuyId") Long groupBuyId, @Param("organizerId") Long organizerId);

    @Query("select new com.camgong.groupbuy.check.CheckQueryRepository$Raw(p.memberId, m.name, p.quantity, p.paidAt, p.receivedAt) "
            + "from Participation p join p.member m where p.groupBuyId = :groupBuyId and p.memberId <> :organizerId "
            + "and p.receivedAt is null order by p.id")
    List<Raw> findUnreceived(@Param("groupBuyId") Long groupBuyId, @Param("organizerId") Long organizerId);

    @Query("select new com.camgong.groupbuy.check.CheckQueryRepository$Raw(p.memberId, m.name, p.quantity, p.paidAt, p.receivedAt) "
            + "from Participation p join p.member m where p.groupBuyId = :groupBuyId and p.memberId = :memberId")
    List<Raw> findOne(@Param("groupBuyId") Long groupBuyId, @Param("memberId") Long memberId);
}

package com.camgong.groupbuy.check;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.camgong.groupbuy.Participation;

/**
 * 체크표 조회. 003 의 개설자 명단과 같은 방식(필요한 컬럼만 조인하는 DTO 프로젝션, 지연 로딩 없음)에 확인 시각 두 컬럼을 더했다.
 * 명단·공구 상태에는 잠금 읽기가 필요 없다: 체크는 COMPLETED 공구에서만 하고 COMPLETED 는 종료 상태라 공구·참여 행의 구성(누가, 몇 개)이
 * 이후 바뀌지 않는다(004·005 의 잠금 읽기 규칙과 다른 점). 단 paid_at/received_at 은 COMPLETED 뒤에도 바뀌므로, 체크 요청의 응답 행은
 * 서비스가 UPDATE 뒤에 FOR SHARE 로 다시 읽는다(ParticipationCheckService.readCommitted).
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

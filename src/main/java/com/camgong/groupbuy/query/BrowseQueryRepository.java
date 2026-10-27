package com.camgong.groupbuy.query;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.camgong.groupbuy.Participation;

/**
 * 상세 명단과 내 활동의 읽기 쿼리. 필요한 컬럼만 조인으로 읽는 DTO 프로젝션이라 엔티티 지연 로딩이 없다
 * (N+1 개선 ④, docs/benchmarks/browse-n-plus-one.md).
 */
public interface BrowseQueryRepository extends Repository<Participation, Long> {

    /** 개설자용 명단: 참여 순서(participation.id)대로. */
    @Query("select new com.camgong.groupbuy.query.ParticipantView(p.memberId, m.name, p.quantity) "
            + "from Participation p join p.member m where p.groupBuyId = :groupBuyId order by p.id")
    List<ParticipantView> findRoster(@Param("groupBuyId") Long groupBuyId);

    /** 내가 연 공구, 개설 시각이 늦은 순(id 역순) 키셋. cursor 가 null 이면 첫 페이지. */
    @Query("select new com.camgong.groupbuy.query.MyActivityRow(g.id, g.title, g.itemName, g.category, g.unitPrice, "
            + "g.securedQuantity, g.totalQuantity, g.deadline, g.status) from GroupBuy g "
            + "where g.organizerId = :me and g.status <> com.camgong.groupbuy.GroupBuyStatus.CANCELLED "
            + "and (:cursor is null or g.id < :cursor) order by g.id desc")
    List<MyActivityRow> findOrganized(@Param("me") Long me, @Param("cursor") Long cursor, Limit limit);

    /** 내가 참여한 공구(내가 연 공구 포함), 공구 id 역순 키셋. */
    @Query("select new com.camgong.groupbuy.query.MyActivityRow(g.id, g.title, g.itemName, g.category, g.unitPrice, "
            + "g.securedQuantity, g.totalQuantity, g.deadline, g.status, p.quantity) "
            + "from Participation p join p.groupBuy g where p.memberId = :me "
            + "and g.status <> com.camgong.groupbuy.GroupBuyStatus.CANCELLED "
            + "and (:cursor is null or g.id < :cursor) order by g.id desc")
    List<MyActivityRow> findParticipated(@Param("me") Long me, @Param("cursor") Long cursor, Limit limit);
}

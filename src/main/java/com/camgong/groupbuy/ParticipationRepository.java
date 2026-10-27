package com.camgong.groupbuy;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    List<Participation> findByGroupBuyId(Long groupBuyId);

    /*
     * 수정·취소처럼 공구 행 락을 잡은 뒤 읽는 참여 행은 잠금 읽기로 읽는다.
     * 일반 SELECT 는 REPEATABLE READ 에서 트랜잭션 첫 읽기 시점(락 대기 전)의 스냅샷을 보므로,
     * 락을 기다리는 동안 다른 요청이 바꾼 값을 못 본다(ModifyConcurrencyIT 가 잡은 버그).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Participation> findLockedByGroupBuyId(Long groupBuyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Participation> findLockedByGroupBuyIdAndMemberId(Long groupBuyId, Long memberId);

    Optional<Participation> findByGroupBuyIdAndMemberId(Long groupBuyId, Long memberId);

    // --- 조회(003) ---

    long countByGroupBuyId(Long groupBuyId);

    /** 개설자용 명단. 순진한 구현: 이름은 호출 쪽이 participation.getMember() 지연 로딩으로 읽는다(N+1 기준선). */
    @Query("select p from Participation p join fetch p.member where p.groupBuyId = :groupBuyId order by p.id")
    List<Participation> findByGroupBuyIdOrderByIdAsc(@Param("groupBuyId") Long groupBuyId);

    /**
     * 내가 참여한 공구(내가 연 공구 포함), 공구 id 역순 키셋. 순진한 구현: 참여 행만 읽고
     * 호출 쪽이 participation.getGroupBuy() 를 지연 로딩한다(1 + N 쿼리, N+1 기준선).
     */
    @Query("select p from Participation p join fetch p.groupBuy g where p.memberId = :me "
            + "and g.status <> com.camgong.groupbuy.GroupBuyStatus.CANCELLED "
            + "and (:cursor is null or p.groupBuyId < :cursor) order by p.groupBuyId desc")
    List<Participation> findMine(@Param("me") Long me, @Param("cursor") Long cursor, Limit limit);
}

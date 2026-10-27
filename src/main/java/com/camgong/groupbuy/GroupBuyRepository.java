package com.camgong.groupbuy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface GroupBuyRepository extends JpaRepository<GroupBuy, Long> {

    /** 수정·취소를 공구 행 단위로 직렬화한다(SELECT ... FOR UPDATE, research 결정 4). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GroupBuy g where g.id = :id")
    Optional<GroupBuy> findByIdForUpdate(@Param("id") Long id);

    /** 상세용: 공구와 개설자 이름을 한 번에 읽는다(research 결정 7 의 1번 쿼리). */
    @Query("select g from GroupBuy g join fetch g.organizer where g.id = :id and g.status <> com.camgong.groupbuy.GroupBuyStatus.CANCELLED")
    Optional<GroupBuy> findDetailById(@Param("id") Long id);

    /** 마감 확정 후보 id(락 없음). 마감이 이른 순. excluded 는 이번 실행에서 이미 시도한 id(실패·건너뜀이 앞쪽을 막지 않게 한다). 실제 확정은 건별 선점이 조건을 다시 확인한다. */
    @Query("select g.id from GroupBuy g where g.status = com.camgong.groupbuy.GroupBuyStatus.OPEN "
            + "and g.deadline <= :cutoff and g.securedQuantity < g.totalQuantity and g.id not in :excluded "
            + "order by g.deadline")
    List<Long> findCloseCandidates(@Param("cutoff") Instant cutoff, @Param("excluded") java.util.Collection<Long> excluded,
            Limit limit);

    /**
     * 건별 선점: id 하나를 지정해 SKIP LOCKED 로 잡는다. 이미 다른 곳이 잡았거나 확정됐거나 조건이 안 맞으면 비어 있다.
     * 범위를 훑는 형태(ORDER BY … LIMIT 1 … FOR UPDATE SKIP LOCKED)는 REPEATABLE READ 의 갭 락 때문에 다른 워커의
     * 갱신이 락 대기 시간 초과로 실패하므로 쓰지 않는다(spikes 5').
     */
    @Query(value = "SELECT * FROM group_buy WHERE id = :id AND status = 'OPEN' AND deadline <= :cutoff "
            + "AND secured_quantity < total_quantity FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<GroupBuy> claimForClose(@Param("id") Long id, @Param("cutoff") Instant cutoff);
}

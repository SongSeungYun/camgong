package com.camgong.groupbuy;

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

    /** 내가 연 공구, 개설 시각이 늦은 순(id 역순) 키셋. cursor 가 null 이면 첫 페이지. */
    @Query("select g from GroupBuy g where g.organizerId = :me and g.status <> com.camgong.groupbuy.GroupBuyStatus.CANCELLED "
            + "and (:cursor is null or g.id < :cursor) order by g.id desc")
    List<GroupBuy> findMine(@Param("me") Long me, @Param("cursor") Long cursor, Limit limit);
}

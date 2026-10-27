package com.camgong.groupbuy;

import java.util.Optional;

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
}

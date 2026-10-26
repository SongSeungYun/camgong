package com.camgong.groupbuy;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

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
}

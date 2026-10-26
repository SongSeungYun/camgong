package com.camgong.groupbuy;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    Optional<Participation> findByGroupBuyIdAndMemberId(Long groupBuyId, Long memberId);
}

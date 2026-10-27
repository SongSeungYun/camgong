package com.camgong.member;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmailKey(String emailKey);

    Optional<Member> findByEmailKey(String emailKey);

    boolean existsByDisplayNameKey(String displayNameKey);
}

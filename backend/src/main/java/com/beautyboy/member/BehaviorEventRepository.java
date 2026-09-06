package com.beautyboy.member;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BehaviorEventRepository extends JpaRepository<BehaviorEvent, Long> {

    long countByMemberId(Long memberId);
}

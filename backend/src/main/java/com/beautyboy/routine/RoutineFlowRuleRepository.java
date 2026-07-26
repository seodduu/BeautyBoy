package com.beautyboy.routine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoutineFlowRuleRepository extends JpaRepository<RoutineFlowRule, Long> {

    /** 규칙 전량을 우선순위(priority) 오름차순, 동률 시 id 오름차순으로 조회. Task 4의 매칭 순회가 소비. */
    List<RoutineFlowRule> findAllByOrderByPriorityAscIdAsc();
}

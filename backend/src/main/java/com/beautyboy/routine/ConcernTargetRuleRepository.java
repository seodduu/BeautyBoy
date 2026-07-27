package com.beautyboy.routine;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 규칙 전량을 기동 시 1회 읽어 캐싱하는 것이 유일한 용도라(설계 §5.2) findAll()만 쓴다.
 * 조회 메서드를 추가하지 않는다 — 쓰는 데가 없다.
 */
public interface ConcernTargetRuleRepository extends JpaRepository<ConcernTargetRule, Long> {
}

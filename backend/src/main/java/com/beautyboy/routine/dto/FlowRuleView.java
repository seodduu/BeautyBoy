package com.beautyboy.routine.dto;

import com.beautyboy.routine.RoutineFlowRule;

/**
 * 전이 규칙 한 줄의 배포 형태(설계 §5.2). 기기 측이 같은 매칭을 로컬에서 돌리려면 규칙의
 * 판정 필드가 그대로 필요하므로 엔티티와 1:1이다 — id만 뺀다(클라이언트가 쓸 일이 없고,
 * 시드 재적용 때 값이 바뀌면 내용이 같은데도 version이 흔들린다).
 */
public record FlowRuleView(String fromCategoryCode, String fromTagSlug, String toCategoryCode,
                           String toTagSlug, String edgeKind, String reason, int priority) {

    public static FlowRuleView from(RoutineFlowRule rule) {
        return new FlowRuleView(rule.getFromCategoryCode(), rule.getFromTagSlug(), rule.getToCategoryCode(),
                rule.getToTagSlug(), rule.getEdgeKind(), rule.getReason(), rule.getPriority());
    }
}

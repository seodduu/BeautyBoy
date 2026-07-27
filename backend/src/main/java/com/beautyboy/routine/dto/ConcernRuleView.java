package com.beautyboy.routine.dto;

import com.beautyboy.routine.ConcernTargetRule;

/** 고민 → 목표 단계 규칙 한 줄의 배포 형태(설계 §5.2). {@link FlowRuleView}와 같은 이유로 id는 뺀다. */
public record ConcernRuleView(String concernTagSlug, String toCategoryCode, String toTagSlug,
                              String reason, int priority) {

    public static ConcernRuleView from(ConcernTargetRule rule) {
        return new ConcernRuleView(rule.getConcernTagSlug(), rule.getToCategoryCode(), rule.getToTagSlug(),
                rule.getReason(), rule.getPriority());
    }
}

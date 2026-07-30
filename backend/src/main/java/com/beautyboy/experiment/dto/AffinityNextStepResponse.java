package com.beautyboy.experiment.dto;

import java.util.List;

/**
 * 단계별 조합 결과 — 클라이언트 {@code StepComposition}과 1:1이다. 다만 상품 전량이 아니라
 * {@code goodsNo}만 돌려준다: 비교 대상은 "무엇을 골랐는가"이고, 상품 표현을 채워 넣는 일은
 * 두 구현 모두에게 없는 비용이라 실으면 서버 쪽에만 직렬화 비용이 붙는다.
 */
public record AffinityNextStepResponse(List<StepComposition> steps) {

    /**
     * @param pick   null이면 후보 풀이 비었다(클라 {@code pick: null}과 같은 의미 — 화면은 기준선 폴백)
     * @param reason null 가능(폴백 사다리 끝)
     */
    public record StepComposition(String stepId, Long pick, List<Long> alternatives, String reason,
                                  List<String> matchedConcerns, List<String> matchedBehaviors) {
    }
}

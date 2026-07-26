package com.beautyboy.routine.dto;

import com.beautyboy.catalog.dto.GoodsListItem;

import java.util.List;

/**
 * 다음 단계 추천 한 블록. 화면 문구(reason)는 규칙 행이 유일한 출처이므로 그대로 실어 나른다
 * (설계 §3 — 코드·프론트에 문구를 하드코딩하지 않는다).
 *
 * @param edgeKind NEXT_STEP | PAIRED_REMOVAL | BUFFER
 * @param reason   routine_flow_rule.reason 원문
 * @param items    블록당 최대 4개. GoodsListItem은 동결 계약이므로 그대로 싣는다
 */
public record NextStepBlock(
        String edgeKind,
        String reason,
        List<GoodsListItem> items) {
}

package com.beautyboy.routine.dto;

import java.util.List;

/**
 * GET /api/v1/goods/{goodsNo}/next-step 응답. 최대 2블록
 * (순방향 NEXT_STEP·BUFFER 중 1개 + PAIRED_REMOVAL 1개, 설계 §4).
 * 후보가 하나도 없으면 빈 배열 — 빈 슬롯을 화면에 내지 않는다.
 */
public record NextStepResponse(List<NextStepBlock> blocks) {
}

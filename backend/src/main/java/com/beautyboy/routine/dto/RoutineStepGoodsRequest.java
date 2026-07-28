package com.beautyboy.routine.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 루틴 단계 추천 상품 전체 교체 요청. "추가"가 아니라 이 단계의 추천 목록을 통째로 이 값으로 바꾼다.
 *
 * <p>{@code @NotEmpty}가 아니라 {@code @NotNull}인 이유: 빈 배열은 그 단계의 추천을 비운다는 뜻의
 * 유효한 요청이다(§2 결정 2). {@code null}일 때만 막는다 — null이면
 * {@link com.beautyboy.catalog.GoodsQueryService#findListItems}에서 NPE로 샌다.
 */
public record RoutineStepGoodsRequest(@NotNull List<Long> goodsNos) {
}

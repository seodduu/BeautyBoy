package com.beautyboy.routine.dto;

import java.util.List;

/** 루틴 단계 추천 상품 전체 교체 요청. "추가"가 아니라 이 단계의 추천 목록을 통째로 이 값으로 바꾼다. */
public record RoutineStepGoodsRequest(List<Long> goodsNos) {
}

package com.beautyboy.ingredient.dto;

import java.util.List;

/**
 * 상품 성분 종합판정(설계 §0.3~§0.4). 숫자 점수 대신 4단계 안내형 문구를 낸다.
 *
 * @param verdictCode NO_CONCERN|MOSTLY_FINE|CHECK_SENSITIVE|CAUTION|REVIEW
 * @param verdictText 위 코드에 대응하는 한국어 문구
 * @param checkCount  확인 성분 수 N(착향제+각질산, rinse-off 보정 전). 화면 보조 정보로만 쓴다.
 * @param rinseOff    씻어내는 제품 여부(카테고리에서 파생). 판정 시 N을 1 낮춘다.
 * @param flagged     플래그된 성분만(무플래그 제외), sort_order 순.
 */
public record GoodsAssessmentResponse(
        Long goodsNo,
        String verdictCode,
        String verdictText,
        int checkCount,
        boolean rinseOff,
        List<FlaggedIngredient> flagged) {
}

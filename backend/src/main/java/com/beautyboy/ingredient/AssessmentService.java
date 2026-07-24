package com.beautyboy.ingredient;

import com.beautyboy.ingredient.dto.GoodsAssessmentResponse;

public interface AssessmentService {

    /**
     * 상품의 성분을 규제 플래그에 조인해 종합판정을 파생한다(설계 §0.3~§0.4).
     * 존재하지 않거나 숨긴 상품이면 GOODS_NOT_FOUND.
     */
    GoodsAssessmentResponse assess(Long goodsNo);
}

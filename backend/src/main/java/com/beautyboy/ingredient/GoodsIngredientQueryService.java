package com.beautyboy.ingredient;

import com.beautyboy.ingredient.dto.IngredientBadge;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 타 도메인 진입점 — Wave 3 궁합 엔진이 이 인터페이스만 본다(패키지 = 서비스 경계).
 */
public interface GoodsIngredientQueryService {

    /** 상품별 성분 분류 집합. Wave 3 궁합 엔진이 분류 쌍 규칙을 적용할 입력. */
    Map<Long, Set<String>> findCategoriesByGoodsIds(Collection<Long> goodsIds);

    List<IngredientBadge> findBadges(Long goodsId);
}

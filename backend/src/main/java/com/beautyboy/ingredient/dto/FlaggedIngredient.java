package com.beautyboy.ingredient.dto;

import java.util.List;

/**
 * 판정에 반영된 성분 한 건(무플래그 성분은 제외된다).
 *
 * @param flags     성분이 가진 규제/주의 플래그. 예: ["ALLERGEN"], ["LIMIT","EXFOLIANT_ACID"].
 * @param axis      화면 분류 — CHECK(착향제/각질산=확인필요) | INFO(한도=참고) | REVIEW(금지=검토).
 * @param sourceRef 대표 근거 1건(CHECK > REVIEW > INFO 우선). 없으면 null.
 */
public record FlaggedIngredient(
        Long ingredientId,
        String name,
        String inciName,
        List<String> flags,
        String axis,
        String sourceRef) {
}

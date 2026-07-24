package com.beautyboy.ingredient.dto;

import java.util.List;

/**
 * 판정에 반영된 성분 한 건(무플래그 성분은 제외된다).
 *
 * @param summary   성분별 설명(ingredient.summary). 성분마다 달라 템플릿 반복처럼 보이지 않게 한다.
 * @param flags     성분이 가진 규제/주의 플래그. 예: ["ALLERGEN"], ["LIMIT","EXFOLIANT_ACID"].
 * @param axis      화면 분류 — CHECK(착향제/각질산=확인필요) | INFO(한도=참고) | REVIEW(금지=검토).
 * @param acidClass 각질산 계열("AHA"|"BHA"). 이건 <b>분류</b>지 평가 근거가 아니므로 별도로 낸다. 아니면 null.
 * @param limitText 배합한도 원문(식약처 사용제한 원료정보). 한도 성분일 때만, 실제 규제 근거. 아니면 null.
 */
public record FlaggedIngredient(
        Long ingredientId,
        String name,
        String inciName,
        String summary,
        List<String> flags,
        String axis,
        String acidClass,
        String limitText) {
}

package com.beautyboy.ingredient;

import java.util.Map;

/**
 * 성분 카테고리 코드 → 한글 표시명. 카테고리의 소유 도메인(ingredient)이 표시명도 소유한다.
 * 코드 목록의 원천은 V11__ingredient.sql 주석의 14종 — 여기와 어긋나면 이 클래스가 아니라
 * 데이터가 먼저다(스키마·픽스처가 진실).
 *
 * <p>미지 코드는 코드를 그대로 반환한다 — 라벨 누락으로 정보가 사라지는 것보다
 * 코드가 노출되는 편이 낫고, 노출되면 눈에 띄어 고쳐진다.
 */
public final class IngredientCategoryLabels {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("RETINOID", "레티노이드"),
            Map.entry("AHA", "AHA"),
            Map.entry("BHA", "BHA"),
            Map.entry("VITAMIN_C", "비타민C"),
            Map.entry("NIACINAMIDE", "나이아신아마이드"),
            Map.entry("HYALURONIC", "히알루론산"),
            Map.entry("CERAMIDE", "세라마이드"),
            Map.entry("PEPTIDE", "펩타이드"),
            Map.entry("CENTELLA", "센텔라"),
            Map.entry("SALICYLIC", "살리실산"),
            Map.entry("FRAGRANCE", "향료"),
            Map.entry("ALCOHOL", "알코올"),
            Map.entry("SPF_FILTER", "자외선 차단 성분"),
            Map.entry("OTHER", "기타"));

    private IngredientCategoryLabels() {
    }

    public static String labelOf(String categoryCode) {
        return LABELS.getOrDefault(categoryCode, categoryCode);
    }
}

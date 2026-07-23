package com.beautyboy.search.dto;

import java.util.List;

/**
 * 검색 결과 카드 1건.
 *
 * <p>catalog의 {@code GoodsListItem}과 필드가 같지만 그 타입을 import하지 않는다 —
 * 패키지 경계 규칙(타 도메인 타입 직접 참조 금지)이고, catalog는 이번 웨이브에서 T2 소유다.
 * 프론트 입장에서 형태가 같으므로 카드 컴포넌트는 그대로 재사용된다.
 *
 * <p>rating/reviewCount/wished/todayDreamAvailable은 이 웨이브가 채울 수 없어 기본값으로 낸다
 * (설계 7장 동결 계약 — 미래 필드를 지금 형태에 포함해 두면 값이 채워질 때 프론트를 고치지 않는다).
 */
public record SearchResultItem(
        Long goodsNo,
        String brandName,
        String name,
        String thumbnailUrl,
        int listPrice,
        int salePrice,
        int discountRate,
        List<String> badges,
        double rating,
        int reviewCount,
        boolean wished,
        boolean todayDreamAvailable) {
}

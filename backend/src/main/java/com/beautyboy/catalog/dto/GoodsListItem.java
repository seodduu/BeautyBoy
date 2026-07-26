package com.beautyboy.catalog.dto;

import java.util.List;

/**
 * 설계 7장 동결 형태. 필드 순서·타입을 임의로 바꾸지 않는다.
 * 이 웨이브가 못 채우는 값(rating/reviewCount/wished/todayDreamAvailable)은 기본값으로 낸다.
 */
public record GoodsListItem(
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
        boolean todayDreamAvailable,
        List<TagView> tags) {
}

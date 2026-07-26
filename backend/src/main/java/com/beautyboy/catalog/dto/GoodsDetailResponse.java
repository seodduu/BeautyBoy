package com.beautyboy.catalog.dto;

import java.util.List;

/**
 * 설계 2장 "PDP 지연 로딩" 3분할 중 빠른 기본 정보. description은 여기 없다
 * ({@link GoodsDescriptionResponse}가 담당) — 목록 아이템({@link GoodsListItem})과
 * 필드명을 의도적으로 일치시켜(goodsNo, salePrice, badges, discountRate) 프론트가
 * 카드→상세 전환에서 매핑 코드를 새로 쓰지 않게 한다.
 */
public record GoodsDetailResponse(
        Long goodsNo,
        String brandName,
        Long brandId,
        String name,
        String summary,
        String categoryCode,
        List<String> categoryPath,
        String thumbnailUrl,
        int listPrice,
        int salePrice,
        int discountRate,
        List<String> badges,
        String status,
        List<GoodsOptionResponse> options,
        double rating,
        int reviewCount,
        boolean wished,
        boolean todayDreamAvailable,
        List<TagView> tags) {
}

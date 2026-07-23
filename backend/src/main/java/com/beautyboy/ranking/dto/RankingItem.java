package com.beautyboy.ranking.dto;

/**
 * 랭킹 1행. 카드에 필요한 상품 정보를 함께 담는다 —
 * 프론트가 순위 목록을 받고 상품마다 상세를 또 호출하면 N+1 요청이 된다.
 */
public record RankingItem(
        int rank,
        Long goodsNo,
        String brandName,
        String name,
        String thumbnailUrl,
        int listPrice,
        int salePrice,
        int discountRate,
        double score) {
}

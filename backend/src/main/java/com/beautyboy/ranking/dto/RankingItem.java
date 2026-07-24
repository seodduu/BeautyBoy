package com.beautyboy.ranking.dto;

/**
 * 랭킹 1행. 카드에 필요한 상품 정보를 함께 담는다 —
 * 프론트가 순위 목록을 받고 상품마다 상세를 또 호출하면 N+1 요청이 된다.
 *
 * <p>rating/reviewCount/wished는 catalog의 {@code GoodsRatingProvider}/{@code WishedGoodsProvider}를
 * 통해 채워진다(RankingService 참고) — DESIGN.md의 {@code goods-card}는 목록·검색·랭킹·추천·루틴이
 * 전부 재사용하는 단일 카드라 랭킹도 같은 필드를 채워야 한다.
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
        double rating,
        int reviewCount,
        boolean wished,
        double score) {
}

package com.beautyboy.wishlist.dto;

/**
 * 찜 목록 표시용.
 *
 * <p>{@code catalog.GoodsQueryService}는 상품 상세 스냅샷 조회로
 * {@code findOrderSnapshot(goodsNo, optionNo)}만 내주는데, 이는 옵션 단위 주문 스냅샷이라
 * 찜(옵션 없음)의 "목록 카드 표시"에는 맞지 않는다. 그래서 이 응답은 goodsNo만 담고,
 * 카드 표시는 프론트가 goodsNo로 상품 상세를 다시 불러오는 방식으로 축소한다(T3-3 결정).
 */
public record WishlistItemResponse(Long goodsNo) {
}

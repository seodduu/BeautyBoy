package com.beautyboy.cart.dto;

/**
 * 장바구니 표시용. 가격은 <b>조회 시점의 상품 판매가</b>다 —
 * 저장된 값이 아니라 매번 catalog에서 다시 읽는다.
 */
public record CartItemResponse(
        Long cartItemId,
        Long goodsNo,
        Long optionNo,
        String goodsName,
        String optionName,
        int unitPrice,
        int quantity,
        int lineAmount) {
}

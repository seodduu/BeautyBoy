package com.beautyboy.cart.dto;

/**
 * 담기 요청.
 *
 * <p><b>가격 필드가 없는 것이 의도다.</b> 클라이언트가 금액을 보낼 수 있게 두면 언젠가 그 값을 쓰게 된다
 * (CLAUDE.md "돈과 재고는 서버"). 서버가 goodsNo로 가격을 다시 읽는다.
 */
public record CartAddRequest(Long goodsNo, Long optionNo, int quantity) {
}

package com.beautyboy.catalog.dto;

/**
 * PDP 지연 로딩 3분할 중 무거운 본문 조각. 기본 상세 응답(`GoodsDetailResponse`)에는
 * description 필드가 없고, 이 엔드포인트에서만 별도 조회한다.
 */
public record GoodsDescriptionResponse(Long goodsNo, String description) {
}

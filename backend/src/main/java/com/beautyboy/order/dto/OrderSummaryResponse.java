package com.beautyboy.order.dto;

import java.time.LocalDateTime;

/** 주문 목록 1행. 대표 상품명 + 나머지 개수로 "그린티 토너 외 2건"을 프론트가 조립한다. */
public record OrderSummaryResponse(
        String orderNo,
        String status,
        String representativeGoodsName,
        int itemCount,
        int payableAmount,
        LocalDateTime orderedAt) {
}

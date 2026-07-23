package com.beautyboy.order.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 주문 상세. 배송지·금액·상품이 전부 주문 시점 스냅샷이다(현재 상품/회원 정보를 조인하지 않는다). */
public record OrderDetailResponse(
        String orderNo,
        String status,
        int totalAmount,
        int discountAmount,
        int payableAmount,
        String receiverName,
        String receiverPhone,
        String zipcode,
        String address1,
        String address2,
        String deliveryType,
        LocalDateTime orderedAt,
        LocalDateTime paidAt,
        List<OrderItemResponse> items) {

    public record OrderItemResponse(
            String goodsName,
            String optionName,
            int unitPrice,
            int quantity,
            int lineAmount) {
    }
}

package com.beautyboy.order.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세. 배송지·금액·상품이 전부 주문 시점 스냅샷이다(현재 상품/회원 정보를 조인하지 않는다).
 *
 * <p>취소 관련 3종({@code items[].canceledQuantity}·{@code refundedAmount}·{@code cancels})은
 * 설계 §9-2의 계약이다. 잔여 수량은 프론트가 {@code quantity - canceledQuantity}로 계산한다 —
 * 파생값을 응답에 따로 싣지 않는 이유는 두 값이 갈라질 여지를 만들지 않기 위해서다.
 */
public record OrderDetailResponse(
        String orderNo,
        String status,
        int totalAmount,
        int discountAmount,
        int payableAmount,
        int refundedAmount,
        String receiverName,
        String receiverPhone,
        String zipcode,
        String address1,
        String address2,
        String deliveryType,
        LocalDateTime orderedAt,
        LocalDateTime paidAt,
        List<OrderItemResponse> items,
        List<OrderCancelHistoryResponse> cancels) {

    /**
     * {@code orderItemId}가 있는 이유: 취소 요청이 이 id로 항목을 지목한다(설계 §9-1의 요청 바디).
     * 이것이 없으면 화면이 취소 요청을 조립할 수 없다.
     */
    public record OrderItemResponse(
            Long orderItemId,
            String goodsName,
            String optionName,
            int unitPrice,
            int quantity,
            int canceledQuantity,
            int lineAmount) {
    }

    /** 취소 한 회차. 나눠 취소하면 여러 줄이 된다. */
    public record OrderCancelHistoryResponse(
            int refundAmount,
            String reason,
            LocalDateTime canceledAt) {
    }
}

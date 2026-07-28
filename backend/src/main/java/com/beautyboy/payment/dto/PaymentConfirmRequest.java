package com.beautyboy.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 결제 승인 요청.
 *
 * <p>{@code amount}가 있지만 이것은 <b>토스에 전달할 값</b>이지 우리가 신뢰하는 값이 아니다.
 * 최종 판정은 이 amount가 아니라 우리 주문의 payableAmount로 한다 —
 * amount와 payableAmount가 다르면 그 자체가 조작 신호다.
 */
public record PaymentConfirmRequest(
        // order.order_no VARCHAR(30)
        @NotBlank @Size(max = 30) String orderNo,
        // payment.payment_key VARCHAR(200)
        @NotBlank @Size(max = 200) String paymentKey,
        // amount에는 제약을 붙이지 않는다 — 애초에 신뢰하지 않는 값이고 PaymentService가
        // 서버의 payableAmount와 대조한다. @Positive를 붙이면 "검증한다"는 잘못된 인상만 남긴다.
        int amount) {
}

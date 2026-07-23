package com.beautyboy.payment.dto;

/**
 * 결제 승인 요청.
 *
 * <p>{@code amount}가 있지만 이것은 <b>토스에 전달할 값</b>이지 우리가 신뢰하는 값이 아니다.
 * 최종 판정은 이 amount가 아니라 우리 주문의 payableAmount로 한다 —
 * amount와 payableAmount가 다르면 그 자체가 조작 신호다.
 */
public record PaymentConfirmRequest(String orderNo, String paymentKey, int amount) {
}

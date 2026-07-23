package com.beautyboy.payment.dto;

/**
 * 토스 승인 응답에서 우리가 쓰는 값만 추린 것.
 *
 * @param approvedAmount 토스가 "이만큼 승인했다"고 알려준 금액. 우리 주문의 payableAmount와 대조할 대상이다.
 * @param rawJson        응답 원문. payment.raw_response에 그대로 저장한다 — 분쟁 시 근거는 우리 해석이 아니라 원문이다.
 */
public record PaymentApproval(String paymentKey, int approvedAmount, String status, String rawJson) {
}

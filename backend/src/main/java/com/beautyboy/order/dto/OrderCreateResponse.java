package com.beautyboy.order.dto;

/**
 * 주문 생성 결과. 프론트가 이 두 값으로 토스 결제창을 연다(설계 7장 결제 2단계 2항).
 * payableAmount는 서버가 계산한 값이며, 승인 검증의 기준이기도 하다.
 */
public record OrderCreateResponse(String orderNo, int payableAmount) {
}

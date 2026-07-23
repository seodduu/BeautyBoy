package com.beautyboy.payment.dto;

public record PaymentConfirmResponse(String orderNo, String status, int paidAmount) {
}

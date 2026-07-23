package com.beautyboy.payment;

/** 토스 호출 자체의 실패(통신·4xx·5xx). 도메인 규칙 위반(금액 불일치)과 구분한다. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}

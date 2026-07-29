package com.beautyboy.payment;

/** 토스 호출 자체의 실패(통신·4xx·5xx). 도메인 규칙 위반(금액 불일치)과 구분한다. */
public class PaymentGatewayException extends RuntimeException {

    /** true = 토스가 HTTP 응답을 준 실패. 외부 조작이 일어나지 않았음이 확실하다(설계 §5-2). */
    private final boolean definiteFailure;

    /** 토스 에러 코드(예: ALREADY_CANCELED_PAYMENT). 응답이 없거나 파싱 실패면 null. */
    private final String gatewayErrorCode;

    public PaymentGatewayException(String message, Throwable cause,
                                   boolean definiteFailure, String gatewayErrorCode) {
        super(message, cause);
        this.definiteFailure = definiteFailure;
        this.gatewayErrorCode = gatewayErrorCode;
    }

    /** 기존 호출부 호환용 — 응답 없는 실패로 취급한다. */
    public PaymentGatewayException(String message, Throwable cause) {
        this(message, cause, false, null);
    }

    public boolean isDefiniteFailure() { return definiteFailure; }
    public String getGatewayErrorCode() { return gatewayErrorCode; }
}

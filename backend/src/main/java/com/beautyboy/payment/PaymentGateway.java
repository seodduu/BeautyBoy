package com.beautyboy.payment;

import com.beautyboy.payment.dto.PaymentApproval;

/**
 * 결제사 호출 경계.
 *
 * <p>외부 호출을 인터페이스로 가른 이유가 둘이다.
 * (1) 유닛테스트가 네트워크·시크릿 없이 돈다(로드맵 §5 터미널 병렬 안전).
 * (2) 금액 불일치 → 취소 호출 같은 실패 경로를 가짜 구현으로 결정적으로 재현할 수 있다.
 *
 * <p>구현이 던지는 {@link PaymentGatewayException}은 "토스와 통신 자체가 실패"를 뜻한다 —
 * 금액 불일치는 여기서 판단하지 않는다(그건 우리 도메인 규칙이라 PaymentService의 몫이다).
 */
public interface PaymentGateway {

    /**
     * 승인 요청. 토스가 성공을 반환하면 그 시점에 실제로 돈이 움직인다.
     *
     * @param amount 우리가 승인을 요청하는 금액. 토스는 결제창에서 확정된 금액과 이 값이 다르면 거부한다.
     * @return 승인 결과. 금액 검증은 호출자가 한다.
     * @throws PaymentGatewayException 토스 호출이 4xx/5xx이거나 네트워크가 끊긴 경우
     */
    PaymentApproval confirm(String paymentKey, String orderNo, int amount);

    /**
     * 승인 취소. 우리 검증(금액 대조)이 실패했을 때, 이미 승인된 결제를 되돌린다.
     * 취소마저 실패하면 예외가 오르지만, 그때는 이미 승인 취소가 필요하다는 사실이 로그에 남아야 한다.
     */
    void cancel(String paymentKey, String reason);
}

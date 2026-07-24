package com.beautyboy.payment;

import com.beautyboy.payment.dto.PaymentApproval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * e2e 프로필 전용 결제 게이트웨이. 요청한 금액을 그대로 승인한 것처럼 답한다.
 *
 * <p>왜 필요한가: Playwright E2E가 토스 결제창을 자동화할 수 없다(결정 5). 대신 토스가 성공 시
 * 보내는 리다이렉트를 재현하고, 그 뒤의 승인 검증 경로는 실제 PaymentService 코드로 돌린다.
 * <b>금액 대조 로직은 가짜가 아니다</b> — 가짜인 것은 네트워크 호출뿐이다.
 */
@Component
@Profile("e2e")
public class FakePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentGateway.class);

    @Override
    public PaymentApproval confirm(String paymentKey, String orderNo, int amount) {
        String rawJson = "{\"e2e\":true,\"paymentKey\":\"" + paymentKey
                + "\",\"orderId\":\"" + orderNo
                + "\",\"totalAmount\":" + amount
                + ",\"status\":\"DONE\"}";
        return new PaymentApproval(paymentKey, amount, "DONE", rawJson);
    }

    @Override
    public void cancel(String paymentKey, String reason) {
        log.info("[e2e] 가짜 결제 취소 호출: paymentKey={}, reason={}", paymentKey, reason);
    }
}

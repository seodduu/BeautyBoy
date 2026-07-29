package com.beautyboy.payment;

import com.beautyboy.payment.dto.PaymentApproval;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * loadtest 프로필 전용 결제 게이트웨이. k6 부하테스트가 실제 토스 결제창을 때릴 수 없어
 * 외부 결제 구간만 스텁으로 치환하고, 나머지 승인 경로(금액 대조·저장 등)는 실제 코드로 돌린다.
 *
 * <p>목적이 "우리 서버의 처리 능력"을 재는 것이므로, 토스 응답 시간을 지워버리면
 * confirm 개선 폭이 실제보다 과장돼 잘못된 결론을 낳는다. 그래서 토스 실측 응답시간의
 * 근사치({@value #SIMULATED_LATENCY_MS}ms)를 그대로 재현한다.
 */
@Component
@Profile("loadtest & !e2e")
public class StubPaymentGateway implements PaymentGateway {

    // 토스 실측 응답시간의 근사치. 이 지연이 없으면 confirm 개선 폭이 과장된다.
    private static final long SIMULATED_LATENCY_MS = 100;

    @Override
    public PaymentApproval confirm(String paymentKey, String orderNo, int amount) {
        try {
            Thread.sleep(SIMULATED_LATENCY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 요청 금액을 그대로 승인한다 — 금액 대조 로직이 항상 통과하도록.
        return new PaymentApproval(paymentKey, amount, "DONE", "{\"stub\":true}");
    }

    @Override
    public void cancel(String paymentKey, String reason) {
        /* no-op */
    }

    @Override
    public void cancelPartial(String paymentKey, String reason, int cancelAmount) {
        /* no-op */
    }
}

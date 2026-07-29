package com.beautyboy.payment;

import com.beautyboy.payment.dto.PaymentApproval;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * loadtest 프로필 전용 스텁 게이트웨이 단위 테스트.
 *
 * <p>k6 부하테스트는 실제 토스를 때릴 수 없다 — 검증 대상은 "우리 서버가 결제 승인을
 * 받았다고 믿을 때 걸리는 시간"이지 토스의 응답 시간이 아니다. 그래서 (1) 토스 실측 근사치인
 * 100ms 지연을 실제로 재현하는지, (2) loadtest 프로필이 아니면 이 빈이 뜨지 않아 진짜
 * {@link TossPaymentGateway}와 충돌하지 않는지를 확인한다.
 */
class StubPaymentGatewayTest {

    @Test
    void loadtest_프로필에서는_스텁이_지연과_함께_요청금액을_그대로_승인한다() {
        StubPaymentGateway gateway = new StubPaymentGateway();

        long start = System.nanoTime();
        PaymentApproval approval = gateway.confirm("pk_load", "ORD-1", 29000);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(approval.approvedAmount()).isEqualTo(29000);
        assertThat(elapsedMs).isGreaterThanOrEqualTo(100);
    }

    @Test
    void loadtest_프로필이_아니면_스텁_빈이_없다() {
        new ApplicationContextRunner()
                .withUserConfiguration(StubPaymentGateway.class)
                .run(context -> assertThat(context).doesNotHaveBean(StubPaymentGateway.class));
    }
}

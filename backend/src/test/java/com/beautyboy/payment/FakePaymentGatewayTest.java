package com.beautyboy.payment;

import com.beautyboy.payment.dto.PaymentApproval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * e2e 프로필 가짜 게이트웨이 단위 테스트. 네트워크 없이 요청 금액을 그대로 승인한 것처럼 답하는지 검증한다.
 */
class FakePaymentGatewayTest {

    private FakePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new FakePaymentGateway();
    }

    @Test
    void 가짜_게이트웨이는_요청한_금액을_그대로_승인한다() {
        PaymentApproval approval = gateway.confirm("pk_e2e", "ORD-1", 29000);
        assertThat(approval.approvedAmount()).isEqualTo(29000);
        assertThat(approval.status()).isEqualTo("DONE");
        assertThat(approval.rawJson()).contains("e2e");
    }
}

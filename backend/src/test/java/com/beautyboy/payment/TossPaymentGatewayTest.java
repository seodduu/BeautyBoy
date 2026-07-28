package com.beautyboy.payment;

import com.beautyboy.config.TossProperties;
import com.beautyboy.payment.dto.PaymentApproval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * 토스 게이트웨이 단위 테스트. 실제 토스를 부르지 않는다 —
 * MockRestServiceServer가 RestClient의 요청을 가로채 우리가 정한 응답을 돌려준다.
 * 검증 대상은 "우리가 토스에 올바른 형식으로 요청하고, 응답을 올바르게 해석하는가"다.
 */
class TossPaymentGatewayTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private TossPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // MockRestServiceServer가 심어둔 요청 팩토리를 게이트웨이가 덮어쓰지 않는 배선을 쓴다
        // (덮어쓰면 목이 무력화돼 실제 토스로 요청이 나간다). 타임아웃 발화는 TossPaymentGatewayTimeoutTest가 본다.
        gateway = TossPaymentGateway.withGivenRequestFactory(
                builder,
                new TossProperties("test_sk_secret", "https://api.tosspayments.com", null, null));
    }

    @Test
    void 승인_요청은_Basic_인증과_paymentKey_orderId_amount를_담는다() {
        // 토스는 시크릿 키를 "키:" 형태로 Base64 인코딩한 Basic 인증을 요구한다(콜론 뒤 비밀번호 없음).
        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString("test_sk_secret:".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", expectedAuth))
                .andExpect(jsonPath("$.paymentKey").value("pk_123"))
                .andExpect(jsonPath("$.orderId").value("ORD-1"))
                .andExpect(jsonPath("$.amount").value(16000))
                .andRespond(withSuccess("""
                        {"paymentKey":"pk_123","orderId":"ORD-1","totalAmount":16000,"status":"DONE"}
                        """, MediaType.APPLICATION_JSON));

        PaymentApproval approval = gateway.confirm("pk_123", "ORD-1", 16000);

        assertThat(approval.paymentKey()).isEqualTo("pk_123");
        assertThat(approval.approvedAmount()).isEqualTo(16000);
        assertThat(approval.status()).isEqualTo("DONE");
        // 원문을 그대로 보관하는지 — 분쟁 대비.
        assertThat(approval.rawJson()).contains("\"totalAmount\":16000");
        server.verify();
    }

    @Test
    void 토스가_4xx면_PaymentGatewayException() {
        // 이미 취소된 결제·잘못된 키 등. 우리 도메인 예외가 아니라 게이트웨이 예외로 올린다.
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("""
                                {"code":"ALREADY_PROCESSED_PAYMENT","message":"이미 처리된 결제 입니다."}
                                """)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.confirm("pk_x", "ORD-1", 16000))
                .isInstanceOf(PaymentGatewayException.class);
        server.verify();
    }

    @Test
    void 취소_요청은_cancelReason을_담아_취소_엔드포인트로_간다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/pk_123/cancel"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.cancelReason").value("금액 불일치"))
                .andRespond(withSuccess("{\"status\":\"CANCELED\"}", MediaType.APPLICATION_JSON));

        gateway.cancel("pk_123", "금액 불일치");

        server.verify();
    }
}

package com.beautyboy.payment;

import com.beautyboy.config.TossProperties;
import com.beautyboy.payment.dto.PaymentApproval;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 토스페이먼츠 승인·취소 구현.
 *
 * <p>{@code RestClient}는 spring-boot-starter-web에 이미 들어 있어 새 의존성이 없다(빌드 파일 동결 준수).
 *
 * <p>승인 응답의 {@code totalAmount}를 우리가 요청한 amount와 대조하지 않는다 —
 * 그 검증은 PaymentService가 우리 주문의 payableAmount로 한다. 게이트웨이는 통신만 책임진다.
 */
@Component
@Profile("!e2e")
public class TossPaymentGateway implements PaymentGateway {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TossPaymentGateway(RestClient.Builder builder, TossProperties properties) {
        // 토스 Basic 인증: "시크릿키:"를 Base64로. 콜론 뒤 비밀번호는 비운다.
        String basic = Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Basic " + basic)
                .build();
    }

    @Override
    public PaymentApproval confirm(String paymentKey, String orderNo, int amount) {
        try {
            String body = restClient.post()
                    .uri("/v1/payments/confirm")
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderNo, "amount", amount))
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(body);
            return new PaymentApproval(
                    json.path("paymentKey").asText(),
                    json.path("totalAmount").asInt(),
                    json.path("status").asText(),
                    body);
        } catch (RestClientResponseException e) {
            // 토스가 4xx/5xx를 준 경우. 원문 메시지를 담아 올린다.
            throw new PaymentGatewayException(
                    "토스 승인 실패: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new PaymentGatewayException("토스 승인 응답 처리 실패", e);
        }
    }

    @Override
    public void cancel(String paymentKey, String reason) {
        try {
            restClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                    .body(Map.of("cancelReason", reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // 취소 실패는 심각하다(승인은 됐는데 되돌리지 못함). 예외를 삼키지 않고 올려 로그·후속 처리로 남긴다.
            throw new PaymentGatewayException("토스 승인 취소 실패: paymentKey=" + paymentKey, e);
        }
    }
}

package com.beautyboy.payment;

import com.beautyboy.config.TossProperties;
import com.beautyboy.payment.dto.PaymentApproval;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
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
@Profile("!e2e & !loadtest")
public class TossPaymentGateway implements PaymentGateway {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 생성자가 둘이면 스프링이 주입 대상을 못 고른다 — 운영 생성자를 @Autowired로 지목한다.
    @Autowired
    public TossPaymentGateway(RestClient.Builder builder, TossProperties properties) {
        this(builder, properties, true);
    }

    /**
     * 테스트 배선용. {@code MockRestServiceServer}가 builder에 이미 자기 요청 팩토리를 심어둔
     * 경우에 쓴다 — 그 팩토리를 타임아웃 팩토리로 덮어쓰면 목이 무력화돼 요청이 실제 토스로 나간다.
     * 운영 경로(public 생성자)는 항상 타임아웃 팩토리를 붙인다.
     */
    static TossPaymentGateway withGivenRequestFactory(RestClient.Builder builder, TossProperties properties) {
        return new TossPaymentGateway(builder, properties, false);
    }

    // 생성자를 하나만 public으로 두는 이유: 스프링이 주입 생성자를 고를 때 후보가 둘이면 기본 생성자를 찾다 실패한다.
    private TossPaymentGateway(RestClient.Builder builder, TossProperties properties, boolean applyTimeoutFactory) {
        // 토스 Basic 인증: "시크릿키:"를 Base64로. 콜론 뒤 비밀번호는 비운다.
        String basic = Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
        if (applyTimeoutFactory) {
            // 타임아웃이 없으면 재고 차감이 쥔 goods_option 행 락의 보유 시간이 무제한이 된다.
            builder = builder.requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(
                    ClientHttpRequestFactorySettings.defaults()
                            .withConnectTimeout(properties.connectTimeout())
                            .withReadTimeout(properties.readTimeout())));
        }
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
    public void cancelPartial(String paymentKey, String reason, int cancelAmount) {
        취소_호출(paymentKey, Map.of("cancelReason", reason, "cancelAmount", cancelAmount));
    }

    @Override
    public void cancel(String paymentKey, String reason) {
        취소_호출(paymentKey, Map.of("cancelReason", reason));
    }

    private void 취소_호출(String paymentKey, Map<String, Object> body) {
        try {
            restClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // 응답을 받았다 = 환불이 일어나지 않았음이 확실(4xx) 또는 토스가 명시 거부(5xx 응답).
            throw new PaymentGatewayException(
                    "토스 취소 실패: paymentKey=" + paymentKey + " " + e.getStatusCode(),
                    e, true, 토스_에러코드(e.getResponseBodyAsString()));
        } catch (Exception e) {
            // 응답이 없다(타임아웃 등) = 환불됐는지 모른다. 호출자가 UNVERIFIED로 다룬다.
            throw new PaymentGatewayException("토스 취소 응답 없음: paymentKey=" + paymentKey,
                    e, false, null);
        }
    }

    private String 토스_에러코드(String body) {
        try {
            return objectMapper.readTree(body).path("code").asText(null);
        } catch (Exception e) {
            return null;    // 판정 재료가 없을 뿐, 실패 처리는 계속된다.
        }
    }
}

package com.beautyboy.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beautyboy.config.TossProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * 토스 호출의 타임아웃 상한 테스트.
 *
 * <p>재고 차감이 토스 HTTP 호출 동안 goods_option 행 락을 쥐고 있으므로, 타임아웃이 없으면
 * 락 보유 시간이 무제한이 된다. 이 테스트는 응답을 물고 있는 서버를 세워 타임아웃이
 * 실제로 연결을 끊는지 증명한다.
 */
class TossPaymentGatewayTimeoutTest {

    private HttpServer server;

    @BeforeEach
    void 응답을_물고_있는_서버() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/payments/confirm", exchange -> {
            try {
                Thread.sleep(5_000); // read 타임아웃(0.5s)보다 훨씬 길게 물고 있는다
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void 정리() {
        server.stop(0);
    }

    @Test
    @DisplayName("read 타임아웃이 실제로 발화한다 — 무한 대기(락 보유 무제한)로 퇴행하면 이 테스트가 잡는다")
    void 읽기_타임아웃_발화() {
        TossProperties properties = new TossProperties(
                "test_sk_dummy",
                "http://localhost:" + server.getAddress().getPort(),
                Duration.ofSeconds(1),
                Duration.ofMillis(500));
        TossPaymentGateway gateway = new TossPaymentGateway(RestClient.builder(), properties);

        long start = System.nanoTime();
        assertThatThrownBy(() -> gateway.confirm("pk_test", "BB-TIMEOUT-1", 1000))
                .isInstanceOf(PaymentGatewayException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // 서버는 5초를 물고 있다 — 3초 안에 끝났다면 타임아웃이 끊은 것이다(여유 6배).
        assertThat(elapsedMs).isLessThan(3_000);
    }

    @Test
    @DisplayName("타임아웃 미지정이면 기본값 connect 3s / read 10s")
    void 기본값() {
        TossProperties properties = new TossProperties("sk", null, null, null);
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
    }
}

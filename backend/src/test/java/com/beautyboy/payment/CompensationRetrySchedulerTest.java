package com.beautyboy.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보상 재시도 스케줄러의 판정 검증(설계 §5-4).
 *
 * <p>{@code @Scheduled}는 전역이라 컨텍스트가 뜨는 순간부터 실제로 틱한다. 그 틱이 테스트의
 * 픽스처 사이에 끼어들면 간헐 실패가 되므로 초기 지연을 크게 줘 밀어내고, 테스트는
 * {@code run()}을 직접 부른다({@code ViewCountFlushScheduler}와 같은 관례).
 */
@SpringBootTest
@ActiveProfiles("test")
// 전용 H2 데이터베이스를 쓴다 — 이 클래스는 트랜잭션 없이 실제로 커밋하기 때문이다. 공용
// jdbc:h2:mem:beautyboy를 쓰면 (1) 커밋된 픽스처가 다른 테스트로 새고, (2) create-drop이라
// 이 컨텍스트가 닫힐 때 아직 살아 있는 다른 컨텍스트의 테이블까지 지워 "Table not found"가 난다.
@TestPropertySource(properties = {
        "beautyboy.compensation.initial-delay-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:compretry;MODE=MySQL;DATABASE_TO_LOWER=TRUE"})
class CompensationRetrySchedulerTest {

    private static final String 결제키 = "pk_retry";
    private static final int 환불액 = 24_100;

    @TestConfiguration
    static class 가짜_게이트웨이_설정 {
        @Bean
        @Primary
        FakeCancelGateway fakeCancelGateway() {
            return new FakeCancelGateway();
        }
    }

    @Autowired
    CompensationRetryScheduler scheduler;
    @Autowired
    FakeCancelGateway gateway;
    @Autowired
    PaymentCompensationRepository repository;

    @BeforeEach
    void 초기화() {
        gateway.reset();
        repository.deleteAll();
    }

    @Test
    @DisplayName("PENDING_RETRY를 취소 성공하면 DONE")
    void PENDING_RETRY를_취소성공하면_DONE() {
        Long id = 재시도_대상_저장(LocalDateTime.now());

        scheduler.run();

        assertThat(gateway.recorded()).hasSize(1);
        assertThat(gateway.recorded().get(0).paymentKey()).isEqualTo(결제키);
        assertThat(gateway.recorded().get(0).amount()).as("보상은 전액 취소다").isNull();
        assertThat(상태(id)).isEqualTo(PaymentCompensation.STATUS_DONE);
        assertThat(repository.findById(id).orElseThrow().getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("ALREADY_CANCELED 응답도 DONE — 이전 시도의 타임아웃이 실제로는 성공했던 경우")
    void ALREADY_CANCELED응답도_DONE() {
        Long id = 재시도_대상_저장(LocalDateTime.now());
        gateway.failNext(new PaymentGatewayException(
                "이미 취소됨", null, true, "ALREADY_CANCELED_PAYMENT"));

        scheduler.run();

        assertThat(상태(id)).isEqualTo(PaymentCompensation.STATUS_DONE);
        assertThat(repository.findById(id).orElseThrow().getRetryCount())
                .as("멱등 판정이므로 재시도를 소진하지 않는다").isZero();
    }

    @Test
    @DisplayName("5회 실패하면 FAILED로 승격 — 그 이상 실패하는 취소는 일시 장애가 아니다")
    void 다섯회_실패하면_FAILED로_승격() {
        Long id = 재시도_대상_저장(LocalDateTime.now());

        for (int i = 0; i < 5; i++) {
            gateway.failNext(new PaymentGatewayException("토스 5xx", null, true, "UNKNOWN"));
            scheduler.run();
        }

        PaymentCompensation 행 = repository.findById(id).orElseThrow();
        assertThat(행.getRetryCount()).isEqualTo(5);
        assertThat(행.getStatus()).isEqualTo(PaymentCompensation.STATUS_FAILED);

        // 소진 뒤에는 더 이상 대상이 아니다 — 호출이 늘지 않는다.
        int 지금까지_호출 = gateway.recorded().size();
        scheduler.run();
        assertThat(gateway.recorded()).hasSize(지금까지_호출);
    }

    @Test
    @DisplayName("5분 지난 IN_FLIGHT는 FAILED로 승격 — 커밋은 ms 단위라 오탐이 없다")
    void 오분_지난_IN_FLIGHT는_FAILED로_승격() {
        Long id = 저장(PaymentCompensation.inFlight("ORD-STALE", 결제키,
                PaymentCompensation.ACTION_CANCEL_PARTIAL, 환불액, "단순 변심",
                LocalDateTime.now().minusMinutes(6)));

        scheduler.run();

        assertThat(상태(id)).isEqualTo(PaymentCompensation.STATUS_FAILED);
        assertThat(gateway.recorded()).as("승격은 판정일 뿐 — 토스를 부르지 않는다").isEmpty();
    }

    @Test
    @DisplayName("5분 안 된 IN_FLIGHT는 건드리지 않는다 — 아직 커밋 중일 수 있다")
    void 오분_안_된_IN_FLIGHT는_건드리지_않는다() {
        Long id = 저장(PaymentCompensation.inFlight("ORD-FRESH", 결제키,
                PaymentCompensation.ACTION_CANCEL_PARTIAL, 환불액, "단순 변심",
                LocalDateTime.now().minusMinutes(1)));

        scheduler.run();

        assertThat(상태(id)).isEqualTo(PaymentCompensation.STATUS_IN_FLIGHT);
    }

    @Test
    @DisplayName("5분 지난 UNVERIFIED도 FAILED로 승격 — 결과 불명은 자동 해소되지 않는다")
    void 오분_지난_UNVERIFIED도_FAILED로_승격() {
        PaymentCompensation 행 = PaymentCompensation.inFlight("ORD-UNV", 결제키,
                PaymentCompensation.ACTION_CANCEL_PARTIAL, 환불액, "단순 변심",
                LocalDateTime.now().minusMinutes(10));
        행.resolve(PaymentCompensation.STATUS_UNVERIFIED);
        Long id = 저장(행);

        scheduler.run();

        assertThat(상태(id)).isEqualTo(PaymentCompensation.STATUS_FAILED);
    }

    @Test
    @DisplayName("종결된 행(DONE·VOID)은 오래돼도 건드리지 않는다")
    void 종결된_행은_오래돼도_건드리지_않는다() {
        PaymentCompensation done = PaymentCompensation.inFlight("ORD-DONE", 결제키,
                PaymentCompensation.ACTION_CANCEL_PARTIAL, 환불액, "단순 변심",
                LocalDateTime.now().minusDays(1));
        done.resolve(PaymentCompensation.STATUS_DONE);
        Long doneId = 저장(done);

        PaymentCompensation voided = PaymentCompensation.inFlight("ORD-VOID", 결제키,
                PaymentCompensation.ACTION_CANCEL_PARTIAL, 환불액, "단순 변심",
                LocalDateTime.now().minusDays(1));
        voided.resolve(PaymentCompensation.STATUS_VOID);
        Long voidId = 저장(voided);

        scheduler.run();

        assertThat(상태(doneId)).isEqualTo(PaymentCompensation.STATUS_DONE);
        assertThat(상태(voidId)).isEqualTo(PaymentCompensation.STATUS_VOID);
        assertThat(gateway.recorded()).isEmpty();
    }

    @Test
    @DisplayName("한 건이 실패해도 배치의 나머지는 처리된다 — 행마다 독립 트랜잭션")
    void 한_건이_실패해도_나머지는_처리된다() {
        Long 실패할_행 = 재시도_대상_저장(LocalDateTime.now());
        Long 성공할_행 = 재시도_대상_저장(LocalDateTime.now());
        gateway.failNext(new PaymentGatewayException("토스 5xx", null, true, "UNKNOWN"));

        scheduler.run();

        assertThat(상태(실패할_행)).isEqualTo(PaymentCompensation.STATUS_PENDING_RETRY);
        assertThat(repository.findById(실패할_행).orElseThrow().getRetryCount()).isEqualTo(1);
        assertThat(상태(성공할_행)).isEqualTo(PaymentCompensation.STATUS_DONE);
    }

    private Long 재시도_대상_저장(LocalDateTime createdAt) {
        return 저장(PaymentCompensation.pendingRetry("ORD-" + System.nanoTime(), 결제키,
                PaymentCompensation.ACTION_CANCEL_FULL, 환불액, "승인 후 처리 실패",
                "최초 실패", createdAt));
    }

    private Long 저장(PaymentCompensation 행) {
        return repository.saveAndFlush(행).getId();
    }

    private String 상태(Long id) {
        return repository.findById(id).orElseThrow().getStatus();
    }

    /** 테스트가 만든 행만 보도록 전체 목록을 쓰지 않고 id로 확인한다. */
    @SuppressWarnings("unused")
    private List<PaymentCompensation> 전체() {
        return repository.findAll();
    }
}

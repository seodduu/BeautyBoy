package com.beautyboy.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 보상 행 자동 해소(설계 §5-4). 두 가지를 한다: PENDING_RETRY 재시도와, 오래 머문
 * IN_FLIGHT·UNVERIFIED의 FAILED 승격.
 *
 * <p><b>왜 행마다 독립 트랜잭션인가</b>: 한 건의 실패가 배치 전체를 되돌리면, 앞에서 성공한
 * 재시도의 DONE 표시까지 사라져 다음 주기에 토스를 또 부른다. {@code OutboxRelay}가 같은
 * 이유로 같은 방식을 쓴다 — {@code @Transactional(REQUIRES_NEW)}을 자기 클래스 메서드에
 * 붙이면 자기호출이라 프록시를 타지 않아 조용히 무효가 되므로 {@link TransactionTemplate}로
 * 경계를 직접 연다.
 */
@Component
public class CompensationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(CompensationRetryScheduler.class);

    /** 5회(≈5분): 그 이상 실패하는 취소는 일시 장애가 아니다 — 사람이 본다. */
    static final int MAX_RETRY = 5;
    /** 5분: 커밋은 ms 단위 — 5분 남은 IN_FLIGHT·UNVERIFIED는 확정 사고로 봐도 오탐이 없다. */
    static final Duration STALE_AFTER = Duration.ofMinutes(5);
    /** 이전 시도의 타임아웃이 실제로는 성공했던 경우. 이 판정이 재시도의 멱등성이다. */
    private static final String ALREADY_CANCELED = "ALREADY_CANCELED_PAYMENT";

    private final PaymentCompensationRepository repository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate 행_단위_트랜잭션;

    public CompensationRetryScheduler(PaymentCompensationRepository repository,
                                      PaymentGateway paymentGateway,
                                      PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.paymentGateway = paymentGateway;
        this.행_단위_트랜잭션 = new TransactionTemplate(transactionManager);
        this.행_단위_트랜잭션.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 주기 60초: 토스 장애는 분 단위 안에 풀린다 — 더 짧으면 장애 중 낭비, 더 길면 고객
     * 문의("환불 왜 안 와요")보다 늦다.
     *
     * <p>주기·초기 지연을 프로퍼티로 뺀 이유는 테스트 격리다({@code ViewCountFlushScheduler}와
     * 같은 관례) — {@code @EnableScheduling}은 전역이라 테스트 컨텍스트에서도 실제로 틱한다.
     */
    @Scheduled(fixedDelayString = "${beautyboy.compensation.retry-delay-ms:60000}",
            initialDelayString = "${beautyboy.compensation.initial-delay-ms:60000}")
    public void run() {
        for (PaymentCompensation row : repository.findRetryTargets(MAX_RETRY)) {
            한_건_재시도(row.getId());     // 행마다 독립 트랜잭션 — 한 건의 실패가 배치를 못 죽인다
        }
        승격한다(repository.findStale(
                List.of(PaymentCompensation.STATUS_IN_FLIGHT, PaymentCompensation.STATUS_UNVERIFIED),
                LocalDateTime.now().minus(STALE_AFTER)));
    }

    void 한_건_재시도(Long id) {
        행_단위_트랜잭션.executeWithoutResult(status -> {
            PaymentCompensation row = repository.findById(id).orElseThrow();
            try {
                paymentGateway.cancel(row.getPaymentKey(), row.getReason());
                row.resolve(PaymentCompensation.STATUS_DONE);
            } catch (PaymentGatewayException e) {
                if (ALREADY_CANCELED.equals(e.getGatewayErrorCode())) {
                    row.resolve(PaymentCompensation.STATUS_DONE);   // 멱등 판정 — 재시도를 소진하지 않는다
                    return;
                }
                row.recordFailure(CompensationRecorder.truncate(e.getMessage()));   // retry_count++
                if (row.getRetryCount() >= MAX_RETRY) {
                    row.resolve(PaymentCompensation.STATUS_FAILED);
                    log.error("보상 재시도 소진 orderNo={} paymentKey={} amount={} — 수동 확인 필요",
                            row.getOrderNo(), row.getPaymentKey(), row.getAmount());
                }
            }
        });
    }

    /**
     * 승격은 판정일 뿐이라 토스를 부르지 않는다. IN_FLIGHT는 커밋이 실패했다는 뜻이고
     * UNVERIFIED는 결과 자체를 모른다는 뜻 — 둘 다 자동으로 해소할 수 없으니 사람에게 넘긴다.
     */
    private void 승격한다(List<PaymentCompensation> 오래된_행들) {
        for (PaymentCompensation row : 오래된_행들) {
            행_단위_트랜잭션.executeWithoutResult(status -> {
                PaymentCompensation 다시_읽은_행 = repository.findById(row.getId()).orElseThrow();
                다시_읽은_행.resolve(PaymentCompensation.STATUS_FAILED);
            });
            log.error("보상 행 승격 id={} orderNo={} 이전상태={} — 수동 확인 필요",
                    row.getId(), row.getOrderNo(), row.getStatus());
        }
    }
}

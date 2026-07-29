package com.beautyboy.payment;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 보상 행 기록. 전부 REQUIRES_NEW다 — 호출자의 트랜잭션이 롤백돼도 기록은 남아야
 * "미취소 승인"이 처음으로 관측 가능해진다(설계 §5-1). 이 클래스가 이 기능의 존재 이유다.
 */
@Component
public class CompensationRecorder {

    /** last_error 컬럼 길이(V95). 넘치면 INSERT가 통째로 실패해 기록 자체가 사라진다. */
    private static final int LAST_ERROR_MAX = 500;

    private final PaymentCompensationRepository repository;

    public CompensationRecorder(PaymentCompensationRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long recordInFlight(String orderNo, String paymentKey, int amount, String reason) {
        return repository.save(PaymentCompensation.inFlight(
                orderNo, paymentKey, PaymentCompensation.ACTION_CANCEL_PARTIAL,
                amount, reason, LocalDateTime.now())).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long recordPendingRetry(String orderNo, String paymentKey, int amount,
                                   String reason, String lastError) {
        return repository.save(PaymentCompensation.pendingRetry(
                orderNo, paymentKey, PaymentCompensation.ACTION_CANCEL_FULL,
                amount, reason, truncate(lastError), LocalDateTime.now())).getId();
    }

    /** 게이트웨이 실패 판정(설계 §5-2): 응답 있는 실패 → VOID, 응답 없음 → UNVERIFIED. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAfterGatewayFailure(Long id, PaymentGatewayException e) {
        String status = e.isDefiniteFailure()
                ? PaymentCompensation.STATUS_VOID
                : PaymentCompensation.STATUS_UNVERIFIED;
        repository.updateStatus(id, status, truncate(e.getMessage()), LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDone(Long id) {
        repository.updateStatus(id, PaymentCompensation.STATUS_DONE, null, LocalDateTime.now());
    }

    static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= LAST_ERROR_MAX ? message : message.substring(0, LAST_ERROR_MAX);
    }
}

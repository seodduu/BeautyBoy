package com.beautyboy.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 결제 보상 의도 행(V95). "외부(토스)와 로컬이 어긋났을 수 있다"는 사실을 관측 가능하게 만드는
 * 유일한 장치다 — 이 행이 없으면 미취소 승인이 아무 흔적 없이 사라진다(설계 §5-1).
 *
 * <p><b>상태표(설계 §5-3 — 각 상태는 한 가지 의미만 갖는다):</b>
 * <pre>
 * | status        | 의미                                  | 다음                                        |
 * |---------------|---------------------------------------|---------------------------------------------|
 * | IN_FLIGHT     | 토스 호출 전후, 커밋 확인 전          | DONE / VOID / UNVERIFIED / (5분 경과) FAILED |
 * | DONE          | 외부와 로컬 일치 확인                 | 종결                                        |
 * | VOID          | 외부 조작이 확실히 일어나지 않음      | 종결                                        |
 * | PENDING_RETRY | 미취소 승인 확정 — 자동 재시도 대상   | DONE / (5회 초과) FAILED                    |
 * | UNVERIFIED    | 외부 결과 불명                        | (스케줄러) FAILED                           |
 * | FAILED        | 자동 해소 불가 — 사람 확인            | admin 조회·수동 처리                        |
 * </pre>
 */
@Entity
@Table(name = "payment_compensation")
public class PaymentCompensation {

    public static final String STATUS_IN_FLIGHT = "IN_FLIGHT";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_VOID = "VOID";
    public static final String STATUS_PENDING_RETRY = "PENDING_RETRY";
    public static final String STATUS_UNVERIFIED = "UNVERIFIED";
    public static final String STATUS_FAILED = "FAILED";

    public static final String ACTION_CANCEL_FULL = "CANCEL_FULL";
    public static final String ACTION_CANCEL_PARTIAL = "CANCEL_PARTIAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 30)
    private String orderNo;

    @Column(name = "payment_key", nullable = false, length = 200)
    private String paymentKey;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected PaymentCompensation() {
    }

    private PaymentCompensation(String orderNo, String paymentKey, String action, int amount,
                                String reason, String status, String lastError,
                                LocalDateTime createdAt) {
        this.orderNo = orderNo;
        this.paymentKey = paymentKey;
        this.action = action;
        this.amount = amount;
        this.reason = reason;
        this.status = status;
        this.lastError = lastError;
        this.createdAt = createdAt;
    }

    /** 토스를 부르기 직전의 의도 행. 커밋이 실패하면 이 상태로 남아 스케줄러가 감지한다. */
    public static PaymentCompensation inFlight(String orderNo, String paymentKey, String action,
                                               int amount, String reason, LocalDateTime createdAt) {
        return new PaymentCompensation(orderNo, paymentKey, action, amount, reason,
                STATUS_IN_FLIGHT, null, createdAt);
    }

    /** 미취소 승인이 확정된 행 — 스케줄러의 자동 재시도 대상이다. */
    public static PaymentCompensation pendingRetry(String orderNo, String paymentKey, String action,
                                                   int amount, String reason, String lastError,
                                                   LocalDateTime createdAt) {
        return new PaymentCompensation(orderNo, paymentKey, action, amount, reason,
                STATUS_PENDING_RETRY, lastError, createdAt);
    }

    /** 종결 전이. resolved_at을 함께 찍는다 — 상태만 바꾸면 언제 끝났는지가 사라진다. */
    public void resolve(String status) {
        this.status = status;
        this.resolvedAt = LocalDateTime.now();
    }

    /** 재시도 1회 소진. 상한 판정은 호출자(스케줄러)가 이 값을 보고 한다. */
    public void recordFailure(String lastError) {
        this.retryCount++;
        this.lastError = lastError;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public String getPaymentKey() {
        return paymentKey;
    }

    public String getAction() {
        return action;
    }

    public int getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public String getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
}

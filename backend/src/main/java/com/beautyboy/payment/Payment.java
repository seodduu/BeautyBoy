package com.beautyboy.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 결제 기록. 검증을 통과해 확정된 승인만 저장한다 —
 * 금액 불일치로 취소한 건은 payment 행을 남기지 않는다(주문은 결제대기로 되돌아간다).
 *
 * <p>order_id 유니크 제약(V32)이 이중 승인의 DB 차원 마지막 방어선이다.
 */
@Entity
@Table(name = "payment")
public class Payment {

    public static final String STATUS_APPROVED = "APPROVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_key", nullable = false, length = 200)
    private String paymentKey;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 20)
    private String status;

    @Lob
    @Column(name = "raw_response", nullable = false)
    private String rawResponse;

    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;

    protected Payment() {
    }

    public Payment(Long orderId, String paymentKey, int amount, String rawResponse, LocalDateTime approvedAt) {
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.status = STATUS_APPROVED;
        this.rawResponse = rawResponse;
        this.approvedAt = approvedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getPaymentKey() {
        return paymentKey;
    }

    public int getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }
}

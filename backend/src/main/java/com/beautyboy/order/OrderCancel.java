package com.beautyboy.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 취소 한 회차(V94). 같은 주문을 나눠 취소하면 행이 늘어난다 — 상태를 덮어쓰지 않고 쌓는다.
 *
 * <p>{@code payment_key} 컬럼을 두지 않은 이유는 V94 주석에 있다: 결제 지식은 payment
 * 도메인의 것이고, 취소↔결제 연결은 {@code payment_compensation}과 감사 로그가 보존한다.
 *
 * <p>{@code refundAmount}는 항상 서버 계산값이다(스냅샷 단가 × 취소 수량). 그래서 생성자가
 * 아니라 반영을 마친 뒤 {@link #recordRefund(int)}로 채운다 — 줄을 다 돌기 전에는 알 수 없다.
 */
@Entity
@Table(name = "order_cancel")
public class OrderCancel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "refund_amount", nullable = false)
    private int refundAmount;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "canceled_at", nullable = false)
    private LocalDateTime canceledAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "cancel_id", nullable = false)
    private List<OrderCancelItem> items = new ArrayList<>();

    protected OrderCancel() {
    }

    public OrderCancel(Long orderId, String reason, LocalDateTime canceledAt) {
        this.orderId = orderId;
        this.reason = reason;
        this.canceledAt = canceledAt;
    }

    public void addItem(OrderCancelItem item) {
        this.items.add(item);
    }

    /** 이 회차의 환불액을 확정한다. 반영 루프가 끝난 뒤 한 번만 부른다. */
    public void recordRefund(int refundAmount) {
        this.refundAmount = refundAmount;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public int getRefundAmount() {
        return refundAmount;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCanceledAt() {
        return canceledAt;
    }

    public List<OrderCancelItem> getItems() {
        return items;
    }
}

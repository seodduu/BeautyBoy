package com.beautyboy.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 취소 회차의 한 줄(V94). "어느 주문 항목을 몇 개 취소했는가"만 남긴다.
 *
 * <p>금액을 여기 두지 않는 이유: 단가는 {@code order_item}의 스냅샷이 진실이고,
 * 회차 합계는 {@link OrderCancel#getRefundAmount()}에 이미 있다. 같은 수를 두 곳에 두면
 * 언젠가 갈라진다.
 */
@Entity
@Table(name = "order_cancel_item")
public class OrderCancelItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private int quantity;

    protected OrderCancelItem() {
    }

    public OrderCancelItem(Long orderItemId, int quantity) {
        this.orderItemId = orderItemId;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public int getQuantity() {
        return quantity;
    }
}

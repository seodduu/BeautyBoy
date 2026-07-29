package com.beautyboy.order;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
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
 * 주문. 배송지와 금액은 이 시점의 사실로 고정된다(스냅샷).
 *
 * <p>{@code payableAmount}가 결제 검증의 유일한 기준이다 — 토스가 승인했다고 알려온 금액이
 * 이 값과 다르면 승인을 취소한다(T2-6).
 */
@Entity
@Table(name = "orders")
public class Order {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_PARTIALLY_CANCELED = "PARTIALLY_CANCELED";
    public static final String STATUS_CANCELED = "CANCELED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 30, unique = true)
    private String orderNo;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount;

    @Column(name = "payable_amount", nullable = false)
    private int payableAmount;

    @Column(name = "receiver_name", nullable = false, length = 50)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(nullable = false, length = 10)
    private String zipcode;

    @Column(nullable = false, length = 200)
    private String address1;

    @Column(length = 200)
    private String address2;

    @Column(name = "delivery_type", nullable = false, length = 20)
    private String deliveryType;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(String orderNo, Long memberId, String receiverName, String receiverPhone,
                 String zipcode, String address1, String address2, String deliveryType,
                 LocalDateTime orderedAt) {
        this.orderNo = orderNo;
        this.memberId = memberId;
        this.status = STATUS_PENDING;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.zipcode = zipcode;
        this.address1 = address1;
        this.address2 = address2;
        this.deliveryType = deliveryType;
        this.orderedAt = orderedAt;
        this.discountAmount = 0;
    }

    /** 항목을 추가하고 합계를 다시 계산한다. 금액을 밖에서 주입받지 않는 것이 핵심이다. */
    public void addItem(OrderItem item) {
        this.items.add(item);
        recalculate();
    }

    private void recalculate() {
        this.totalAmount = items.stream().mapToInt(OrderItem::getLineAmount).sum();
        // 1차에서 discountAmount는 항상 0이다. 쿠폰이 생기는 웨이브가 이 자리를 채운다.
        this.payableAmount = this.totalAmount - this.discountAmount;
    }

    /**
     * 결제 완료 전이.
     *
     * <p>결제대기가 아닌 주문에는 전이하지 않는다 — 이미 결제된 주문에 승인이 한 번 더 들어오면
     * 두 번 청구된다. 상태 검사가 그 이중 승인의 첫 방어선이다(두 번째는 payment의 유니크 제약).
     */
    public void markPaid(LocalDateTime paidAt) {
        if (!STATUS_PENDING.equals(this.status)) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        this.status = STATUS_PAID;
        this.paidAt = paidAt;
    }

    /**
     * 취소 가능 상태 검사. PAID·PARTIALLY_CANCELED만 통과한다(설계 §2).
     *
     * <p>PARTIALLY_CANCELED를 통과시키는 이유: 회차로 나눠 취소하는 것이 정상 경로다.
     * 잔여가 0인 항목만 남았는지는 항목 단위 잔여 검증({@code OrderItem.cancel})이 판정한다.
     */
    public void ensureCancelable() {
        if (!STATUS_PAID.equals(status) && !STATUS_PARTIALLY_CANCELED.equals(status)) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
    }

    /**
     * 파생 판정: 모든 항목 잔여 0이면 CANCELED, 아니면 PARTIALLY_CANCELED.
     * 취소 수량을 전부 반영한 <b>뒤</b>에 호출한다 — 상태를 따로 저장하지 않고 매번 다시 센다.
     */
    public String applyCancelStatus() {
        boolean 전량_취소됨 = items.stream().allMatch(i -> i.remainingQuantity() == 0);
        this.status = 전량_취소됨 ? STATUS_CANCELED : STATUS_PARTIALLY_CANCELED;
        return this.status;
    }

    public boolean ownedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getStatus() {
        return status;
    }

    public int getTotalAmount() {
        return totalAmount;
    }

    public int getDiscountAmount() {
        return discountAmount;
    }

    public int getPayableAmount() {
        return payableAmount;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public String getZipcode() {
        return zipcode;
    }

    public String getAddress1() {
        return address1;
    }

    public String getAddress2() {
        return address2;
    }

    public String getDeliveryType() {
        return deliveryType;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}

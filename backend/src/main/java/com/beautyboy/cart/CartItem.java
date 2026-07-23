package com.beautyboy.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 장바구니 항목.
 *
 * <p>가격을 저장하지 않는 이유: 장바구니는 아직 구매가 아니다. 가격을 여기 복사해 두면
 * 할인이 시작돼도 손님이 옛 가격을 보게 되고, 반대로 값이 오르면 옛 가격으로 결제하려 든다.
 * 스냅샷을 뜨는 시점은 <b>주문 생성</b>이다(설계 5장 order_item).
 *
 * <p>{@code memberId}/{@code goodsId}가 엔티티 참조가 아니라 스칼라인 이유:
 * member·catalog는 타 도메인이라 엔티티를 직접 참조할 수 없다(패키지 = 서비스 경계).
 * DB에는 FK가 있지만 그것은 데이터 정합성 장치이고, 코드 결합과는 별개다.
 */
@Entity
@Table(name = "cart_item")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Column(name = "option_id")
    private Long optionId;

    @Column(nullable = false)
    private int quantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CartItem() {
    }

    public CartItem(Long memberId, Long goodsId, Long optionId, int quantity) {
        this.memberId = memberId;
        this.goodsId = goodsId;
        this.optionId = optionId;
        this.quantity = quantity;
    }

    public void addQuantity(int amount) {
        this.quantity += amount;
    }

    public void changeQuantity(int quantity) {
        this.quantity = quantity;
    }

    /** 남의 항목을 조작하지 못하게 하는 소유 검사. 서비스가 수정·삭제 전에 반드시 부른다. */
    public boolean ownedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public Long getOptionId() {
        return optionId;
    }

    public int getQuantity() {
        return quantity;
    }
}

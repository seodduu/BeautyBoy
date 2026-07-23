package com.beautyboy.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 주문 상품. 이름·가격이 전부 복사본이다.
 *
 * <p>{@code goodsId}는 "무엇을 샀는지" 추적(랭킹 집계·재구매)용으로만 남긴다.
 * 화면 표시는 반드시 스냅샷 컬럼을 쓴다 — goods를 조인해 보여주면
 * 상품명이 바뀌었을 때 과거 주문서의 상품명이 따라 바뀐다.
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Column(name = "option_id")
    private Long optionId;

    @Column(name = "goods_name", nullable = false, length = 200)
    private String goodsName;

    @Column(name = "option_name", length = 100)
    private String optionName;

    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_amount", nullable = false)
    private int lineAmount;

    protected OrderItem() {
    }

    public OrderItem(Long goodsId, Long optionId, String goodsName, String optionName,
                     int unitPrice, int quantity) {
        this.goodsId = goodsId;
        this.optionId = optionId;
        this.goodsName = goodsName;
        this.optionName = optionName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        // 합계를 밖에서 받지 않고 여기서 곱한다. 밖에서 받으면 그것이 곧 조작 지점이 된다.
        this.lineAmount = unitPrice * quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public Long getOptionId() {
        return optionId;
    }

    public String getGoodsName() {
        return goodsName;
    }

    public String getOptionName() {
        return optionName;
    }

    public int getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getLineAmount() {
        return lineAmount;
    }
}

package com.beautyboy.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "goods_option")
public class GoodsOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_id", nullable = false)
    private Goods goods;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "add_price", nullable = false)
    private int addPrice;

    @Column(nullable = false)
    private int stock;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected GoodsOption() {
    }

    public GoodsOption(Goods goods, String name, int addPrice, int stock, int sortOrder) {
        this.goods = goods;
        this.name = name;
        this.addPrice = addPrice;
        this.stock = stock;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Goods getGoods() {
        return goods;
    }

    public String getName() {
        return name;
    }

    public int getAddPrice() {
        return addPrice;
    }

    public int getStock() {
        return stock;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}

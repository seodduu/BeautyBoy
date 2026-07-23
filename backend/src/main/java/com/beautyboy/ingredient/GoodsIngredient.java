package com.beautyboy.ingredient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * goods_ingredient.goods_id는 goods 테이블의 FK지만, ingredient 패키지는 catalog.Goods
 * 엔티티/리포지토리를 import하지 않는다(패키지 = 서비스 경계). 그래서 JPA 연관관계 없이
 * Long goodsId 스칼라로만 매핑한다.
 */
@Entity
@Table(name = "goods_ingredient")
@IdClass(GoodsIngredient.Pk.class)
public class GoodsIngredient {

    @Id
    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Id
    @Column(name = "ingredient_id", nullable = false)
    private Long ingredientId;

    @Column(name = "is_key", nullable = false)
    private boolean key;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected GoodsIngredient() {
    }

    public GoodsIngredient(Long goodsId, Long ingredientId, boolean key, int sortOrder) {
        this.goodsId = goodsId;
        this.ingredientId = ingredientId;
        this.key = key;
        this.sortOrder = sortOrder;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public Long getIngredientId() {
        return ingredientId;
    }

    public boolean isKey() {
        return key;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public static class Pk implements Serializable {
        private Long goodsId;
        private Long ingredientId;

        public Pk() {
        }

        public Pk(Long goodsId, Long ingredientId) {
            this.goodsId = goodsId;
            this.ingredientId = ingredientId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(goodsId, pk.goodsId) && Objects.equals(ingredientId, pk.ingredientId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(goodsId, ingredientId);
        }
    }
}

package com.beautyboy.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * goods_tag는 tag·goods·(선택) ingredient 세 테이블을 FK로 참조하지만, 패키지 경계 규칙에
 * 따라 JPA 연관관계를 두지 않고 스칼라 Long만 매핑한다(기존 ingredient.GoodsIngredient의
 * {@code @IdClass Pk} 패턴을 그대로 따른다).
 */
@Entity
@Table(name = "goods_tag")
@IdClass(GoodsTag.Pk.class)
public class GoodsTag {

    @Id
    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Id
    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "source_ingredient_id")
    private Long sourceIngredientId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected GoodsTag() {
    }

    public GoodsTag(Long goodsId, Long tagId, Long sourceIngredientId, int sortOrder) {
        this.goodsId = goodsId;
        this.tagId = tagId;
        this.sourceIngredientId = sourceIngredientId;
        this.sortOrder = sortOrder;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public Long getTagId() {
        return tagId;
    }

    public Long getSourceIngredientId() {
        return sourceIngredientId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public static class Pk implements Serializable {
        private Long goodsId;
        private Long tagId;

        public Pk() {
        }

        public Pk(Long goodsId, Long tagId) {
            this.goodsId = goodsId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(goodsId, pk.goodsId) && Objects.equals(tagId, pk.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(goodsId, tagId);
        }
    }
}

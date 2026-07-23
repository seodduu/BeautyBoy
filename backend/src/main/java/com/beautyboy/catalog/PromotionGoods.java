package com.beautyboy.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * promotion_goods 다대다 연결 테이블. 목록 배지 조회는 엔티티 그래프가 아니라
 * goodsId 목록으로 1쿼리 일괄 조회하므로, FK를 연관관계가 아닌 스칼라로 둔다.
 */
@Entity
@Table(name = "promotion_goods")
@IdClass(PromotionGoods.PromotionGoodsId.class)
public class PromotionGoods {

    @Id
    @Column(name = "promotion_id")
    private Long promotionId;

    @Id
    @Column(name = "goods_id")
    private Long goodsId;

    protected PromotionGoods() {
    }

    public PromotionGoods(Long promotionId, Long goodsId) {
        this.promotionId = promotionId;
        this.goodsId = goodsId;
    }

    public Long getPromotionId() {
        return promotionId;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public static class PromotionGoodsId implements Serializable {
        private Long promotionId;
        private Long goodsId;

        public PromotionGoodsId() {
        }

        public PromotionGoodsId(Long promotionId, Long goodsId) {
            this.promotionId = promotionId;
            this.goodsId = goodsId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PromotionGoodsId that)) return false;
            return Objects.equals(promotionId, that.promotionId) && Objects.equals(goodsId, that.goodsId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(promotionId, goodsId);
        }
    }
}

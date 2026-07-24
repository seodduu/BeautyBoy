package com.beautyboy.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 상품별 평점 평균·개수 비정규화 테이블. 리뷰 작성·삭제 때마다 통째로 재집계해 upsert한다
 * (증분 갱신은 동시성 하에서 값이 어긋날 수 있어 쓰지 않는다).
 */
@Entity
@Table(name = "goods_review_stat")
public class GoodsReviewStat {

    @Id
    @Column(name = "goods_id")
    private Long goodsId;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "rating_sum", nullable = false)
    private int ratingSum;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected GoodsReviewStat() {
    }

    public GoodsReviewStat(Long goodsId) {
        this.goodsId = goodsId;
        this.reviewCount = 0;
        this.ratingSum = 0;
    }

    public void update(int reviewCount, int ratingSum, LocalDateTime updatedAt) {
        this.reviewCount = reviewCount;
        this.ratingSum = ratingSum;
        this.updatedAt = updatedAt;
    }

    public double average() {
        return reviewCount == 0 ? 0.0 : (double) ratingSum / reviewCount;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public int getRatingSum() {
        return ratingSum;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

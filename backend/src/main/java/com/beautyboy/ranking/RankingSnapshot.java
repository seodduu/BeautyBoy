package com.beautyboy.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 매시 배치가 만드는 랭킹 결과 1행. 조회는 이 테이블만 읽는다(설계 5장).
 *
 * <p>조회 시점에 점수를 계산하지 않는 이유: 랭킹은 메인에서 모든 방문자에게 노출되는데
 * 매 요청마다 3일치 통계를 집계·정렬하면 가장 흔한 요청이 가장 무거워진다.
 * 미리 계산해 두고 읽기만 한다.
 *
 * <p>{@code categoryCode}의 {@code "ALL"}은 전체 랭킹을 뜻하는 예약값이다.
 */
@Entity
@Table(name = "ranking_snapshot")
public class RankingSnapshot {

    /** 전체 랭킹을 가리키는 예약 카테고리 코드. 실제 카테고리 코드는 'C'로 시작하므로 충돌하지 않는다. */
    public static final String CATEGORY_ALL = "ALL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_code", nullable = false, length = 12)
    private String categoryCode;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(nullable = false)
    private double score;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    protected RankingSnapshot() {
    }

    public RankingSnapshot(String categoryCode, Long goodsId, int rankNo, double score, LocalDateTime generatedAt) {
        this.categoryCode = categoryCode;
        this.goodsId = goodsId;
        this.rankNo = rankNo;
        this.score = score;
        this.generatedAt = generatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public int getRankNo() {
        return rankNo;
    }

    public double getScore() {
        return score;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }
}

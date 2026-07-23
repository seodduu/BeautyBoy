package com.beautyboy.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 상품×날짜 일별 통계. 랭킹 점수의 원장이다(설계 5장).
 *
 * <p>{@code goodsId}가 Goods 엔티티 참조가 아니라 스칼라인 이유: catalog는 타 도메인이라
 * 엔티티를 직접 참조할 수 없다(패키지 = 서비스 경계). FK도 걸지 않는다 — 통계는 상품이
 * 숨겨지거나 정리돼도 과거 기록으로 남아야 한다.
 *
 * <p>복합 PK (goods_id, stat_date)를 쓰는 이유: 조회수 증가가 "있으면 더하고 없으면 만들기"라
 * upsert(ON DUPLICATE KEY UPDATE)로 한 방에 끝나야 하기 때문이다.
 * 대리키 + unique 조합으로도 되지만 그러면 upsert가 unique 인덱스에 의존해 의도가 흐려진다.
 */
@Entity
@Table(name = "goods_daily_stat")
@IdClass(GoodsDailyStat.Key.class)
public class GoodsDailyStat {

    @Id
    @Column(name = "goods_id")
    private Long goodsId;

    @Id
    @Column(name = "stat_date")
    private LocalDate statDate;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "sales_count", nullable = false)
    private int salesCount;

    @Column(name = "wish_count", nullable = false)
    private int wishCount;

    protected GoodsDailyStat() {
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public LocalDate getStatDate() {
        return statDate;
    }

    public int getViewCount() {
        return viewCount;
    }

    public int getSalesCount() {
        return salesCount;
    }

    public int getWishCount() {
        return wishCount;
    }

    /** 복합 PK. JPA가 요구하는 대로 equals/hashCode와 기본 생성자를 갖춘다. */
    public static class Key implements Serializable {

        private Long goodsId;
        private LocalDate statDate;

        public Key() {
        }

        public Key(Long goodsId, LocalDate statDate) {
            this.goodsId = goodsId;
            this.statDate = statDate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key key)) {
                return false;
            }
            return Objects.equals(goodsId, key.goodsId) && Objects.equals(statDate, key.statDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(goodsId, statDate);
        }
    }
}

package com.beautyboy.routine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 단계별 추천 상품 링크. goods_no는 goods(goods_no)를 논리적으로 가리키되 물리 FK가 없다
 * (패키지 경계 — catalog 소유 테이블을 routine이 잠그지 않는다). 따라서 JPA 연관이 아니라 Long 값이다.
 */
@Entity
@Table(name = "routine_step_goods")
public class RoutineStepGoods {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goods_no", nullable = false)
    private Long goodsNo;

    // V50 스키마가 TINYINT로 정의한다(스키마가 진실). 실 MySQL validate가 int(INTEGER)와
    // TINYINT를 불일치로 보므로 JDBC 타입을 맞춘다 — Wave 1 Ingredient 교훈.
    @Column(name = "sort_order", nullable = false)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private int sortOrder;

    protected RoutineStepGoods() {
    }

    public RoutineStepGoods(Long id, Long goodsNo, int sortOrder) {
        this.id = id;
        this.goodsNo = goodsNo;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getGoodsNo() {
        return goodsNo;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}

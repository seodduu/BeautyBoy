package com.beautyboy.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 리뷰. 구매인증을 통과한 회원만 상품당 1건 쓸 수 있다(DB 유니크 제약이 최종 방어선).
 */
@Entity
@Table(name = "review")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    // V40 스키마가 rating을 TINYINT로 정의한다(스키마가 진실). 실 MySQL의
    // ddl-auto=validate가 int(INTEGER)와 TINYINT를 불일치로 보므로 JDBC 타입을 맞춘다.
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private int rating;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "skin_type_snapshot")
    private String skinTypeSnapshot;

    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Review() {
    }

    public Review(Long memberId, Long goodsId, int rating, String content, String skinTypeSnapshot) {
        this.memberId = memberId;
        this.goodsId = goodsId;
        this.rating = rating;
        this.content = content;
        this.skinTypeSnapshot = skinTypeSnapshot;
    }

    public void increaseHelpful(int delta) {
        this.helpfulCount += delta;
    }

    /**
     * 별점·본문 수정. skinTypeSnapshot은 여기 없다 — 바꿀 수 있는 통로 자체를
     * 만들지 않는 것이 스냅샷 원칙의 집행 방식이다(설계 §2.4).
     */
    public void edit(int rating, String content) {
        this.rating = rating;
        this.content = content;
    }

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

    public int getRating() {
        return rating;
    }

    public String getContent() {
        return content;
    }

    public String getSkinTypeSnapshot() {
        return skinTypeSnapshot;
    }

    public int getHelpfulCount() {
        return helpfulCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

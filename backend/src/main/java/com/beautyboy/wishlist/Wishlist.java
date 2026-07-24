package com.beautyboy.wishlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 찜(위시리스트) 항목.
 *
 * <p>{@code memberId}/{@code goodsId}가 엔티티 참조가 아니라 스칼라인 이유:
 * member·catalog는 타 도메인이라 엔티티를 직접 참조할 수 없다(패키지 = 서비스 경계).
 */
@Entity
@Table(name = "wishlist")
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Wishlist() {
    }

    public Wishlist(Long memberId, Long goodsId) {
        this.memberId = memberId;
        this.goodsId = goodsId;
    }

    /** 남의 찜을 조작하지 못하게 하는 소유 검사. */
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

package com.beautyboy.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 설계 §5: 서버에 남는 행동 로그. 회원·상품·종류·시각 네 칸뿐이다.
 *
 * <p>goods는 catalog 도메인 소유라 연관을 걸지 않고 식별자만 들고 있다(패키지 = 서비스 경계).
 * Address가 memberId만 들고 있는 것과 같은 방식이다.
 */
@Entity
@Table(name = "behavior_event")
public class BehaviorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private BehaviorKind kind;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected BehaviorEvent() {
    }

    public BehaviorEvent(Long memberId, Long goodsId, BehaviorKind kind, LocalDateTime createdAt) {
        this.memberId = memberId;
        this.goodsId = goodsId;
        this.kind = kind;
        this.createdAt = createdAt;
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

    public BehaviorKind getKind() {
        return kind;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

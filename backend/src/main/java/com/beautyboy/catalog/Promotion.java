package com.beautyboy.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 배지(SALE|COUPON|GIFT|ONE_PLUS_ONE)는 goods에 저장하지 않고 이 테이블과의
 * 기간 유효성 조인으로 매 조회마다 파생한다(설계 2장).
 */
@Entity
@Table(name = "promotion")
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "badge_type", nullable = false, length = 20)
    private String badgeType;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    protected Promotion() {
    }

    public Promotion(String name, String badgeType, LocalDateTime startsAt, LocalDateTime endsAt) {
        this.name = name;
        this.badgeType = badgeType;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBadgeType() {
        return badgeType;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }
}

package com.beautyboy.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 주문 알림. 적재까지만이 스코프다(설계 §2-4) — 발송 채널도 읽음 처리도 아직 없다.
 *
 * <p>매핑은 {@code V92__notification.sql}의 DDL이 진실이다. 운영은 {@code ddl-auto=validate}라
 * 컬럼 이름·타입이 어긋나면 앱이 뜨지 않는다. H2는 {@code create-drop}이라 이 엔티티가 곧
 * 스키마가 되므로 H2 녹색은 증거가 되지 못한다 — {@code integrationTest}의 Flyway 스모크가 판정한다.
 *
 * <p>{@code memberId}·{@code eventId}가 연관 참조가 아니라 스칼라인 이유: member와 outbox는
 * 타 도메인/타 패키지라 엔티티를 직접 참조할 수 없다(패키지 = 서비스 경계).
 *
 * <p>유니크 제약 {@code uk_notification_dedup (member_id, event_id)}가 중복 소비를 DB에서 막는다.
 * A5의 컨슈머는 재시도·리밸런싱으로 같은 이벤트를 두 번 받을 수 있는데, 그때 알림이 두 건
 * 생기면 손님에게 그대로 보인다. 애플리케이션 조건문이 아니라 제약으로 막는 이유는
 * 동시에 두 소비가 들어오면 "조회 후 없으면 INSERT"가 둘 다 통과하기 때문이다.
 */
@Entity
@Table(name = "notification",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_dedup", columnNames = {"member_id", "event_id"}))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** outbox_event.id. 중복 소비 판별 키다. */
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "message", nullable = false, length = 200)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(Long memberId, Long eventId, String type, String message, LocalDateTime createdAt) {
        this.memberId = memberId;
        this.eventId = eventId;
        this.type = type;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

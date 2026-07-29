package com.beautyboy.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 트랜잭셔널 아웃박스 행. 확정 트랜잭션 안에서 {@link OutboxAppender}가 INSERT하고,
 * 릴레이(A4)가 폴링해 Kafka로 발행한 뒤 PUBLISHED로 마킹한다.
 *
 * <p>DDL은 V90(설계 §4.1)이 진실이다 — payload는 MySQL JSON 컬럼. Java 쪽은 원본 문자열을
 * 그대로 들고 있는 String 필드에 {@code @JdbcTypeCode(SqlTypes.JSON)}로 컬럼이 JSON임을
 * 명시한다. Hibernate 6 + MySQL/H2(2.x) 둘 다 JSON 타입코드를 네이티브로 인식하므로
 * 실 MySQL validate와 H2 create-drop 양쪽에서 타입이 어긋나지 않는다.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 30)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private String payload;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload,
                        LocalDateTime createdAt) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = STATUS_PENDING;
        this.createdAt = createdAt;
    }

    public void markPublished(LocalDateTime publishedAt) {
        this.status = STATUS_PUBLISHED;
        this.publishedAt = publishedAt;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Long getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }
}

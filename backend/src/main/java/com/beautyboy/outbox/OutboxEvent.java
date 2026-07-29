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
    /**
     * 발행 재시도 임계치를 넘겨 릴레이가 포기한 행(V93). 폴링 쿼리(status='PENDING')에서 빠지므로
     * 뒤 건이 흐른다. 되살리려면 {@code status='PENDING', attempt_count=0}으로 되돌린다.
     */
    public static final String STATUS_FAILED = "FAILED";

    /** {@code last_error} 컬럼 길이(V93). 넘치면 잘라 넣는다 — 진단용 한 줄이지 로그 대체물이 아니다. */
    private static final int 오류_메시지_최대길이 = 500;

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

    /** 발행 시도 횟수(V93). 임계치를 넘으면 {@link #STATUS_FAILED}로 옮겨 릴레이가 건너뛴다. */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** 마지막 발행 실패 사유(V93). 사람이 FAILED 행을 판단할 때 보는 유일한 단서다. */
    @Column(name = "last_error", length = 오류_메시지_최대길이)
    private String lastError;

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

    /**
     * 발행 실패를 한 번 기록한다. 시도 횟수가 {@code maxAttempts}에 도달하면 {@link #STATUS_FAILED}로
     * 옮겨 다음 폴링에서 빠진다 — 이 한 건이 뒤의 모든 이벤트를 막는 것을 여기서 끊는다.
     *
     * @return 이 호출로 포기(FAILED) 처리됐으면 {@code true}. 호출자가 로그 수준을 error로 올릴 신호다.
     */
    public boolean recordFailure(String error, int maxAttempts) {
        this.attemptCount++;
        this.lastError = 자른다(error);
        if (this.attemptCount >= maxAttempts) {
            this.status = STATUS_FAILED;
            return true;
        }
        return false;
    }

    private static String 자른다(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 오류_메시지_최대길이 ? error : error.substring(0, 오류_메시지_최대길이);
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
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

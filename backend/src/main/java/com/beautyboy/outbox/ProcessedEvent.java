package com.beautyboy.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 컨슈머별 처리 기록(설계 §5). "이 이벤트를 이 컨슈머가 이미 처리했다"를 DB에 남겨,
 * 두 번 하면 값이 틀어지는 처리(판매 집계)의 중복 소비를 막는다.
 *
 * <p>매핑은 {@code V91__processed_event.sql}이 진실이다 — PK가 {@code (event_id, consumer)}인
 * 복합키라 대리키를 두지 않는다. 대리키 + unique로 해도 되지만 그러면 멱등성이 "제약 하나"가
 * 아니라 "인덱스에 얹힌 관례"가 되어 의도가 흐려진다.
 *
 * <p>모든 컨슈머가 이 표를 쓰지는 않는다. 장바구니 삭제는 자연 멱등이고 알림은
 * {@code uk_notification_dedup}이 이미 막는다 — 전부에 처리 기록을 다는 것은 과설계다(설계 §5).
 */
@Entity
@Table(name = "processed_event")
@IdClass(ProcessedEvent.Key.class)
public class ProcessedEvent {

    /** 판매 집계 컨슈머의 이름. {@code consumer} 컬럼 값이자 설계 §5 표의 그 이름이다. */
    public static final String CONSUMER_SALES_AGGREGATION = "sales-aggregation";

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Id
    @Column(name = "consumer", length = 50)
    private String consumer;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(Long eventId, String consumer, LocalDateTime processedAt) {
        this.eventId = eventId;
        this.consumer = consumer;
        this.processedAt = processedAt;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getConsumer() {
        return consumer;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    /** 복합 PK. JPA가 요구하는 대로 equals/hashCode와 기본 생성자를 갖춘다({@code GoodsDailyStat.Key}와 같은 형태). */
    public static class Key implements Serializable {

        private Long eventId;
        private String consumer;

        public Key() {
        }

        public Key(Long eventId, String consumer) {
            this.eventId = eventId;
            this.consumer = consumer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key key)) {
                return false;
            }
            return Objects.equals(eventId, key.eventId) && Objects.equals(consumer, key.consumer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, consumer);
        }
    }
}

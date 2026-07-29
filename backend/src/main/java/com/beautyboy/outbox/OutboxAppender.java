package com.beautyboy.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 확정 트랜잭션 안에서 호출된다. REQUIRED 전파 — 자체 트랜잭션을 만들지 않는다.
 * 호출자(A3의 {@code PaymentService.confirm})의 트랜잭션이 커밋되면 아웃박스 행도 함께
 * 커밋되고, 롤백되면 함께 사라진다.
 */
public interface OutboxAppender {

    void appendOrderConfirmed(OrderConfirmedEvent event);
}

/**
 * 기본 구현. {@code aggregate_id = orderId}로 INSERT해 PK(eventId)를 채번한 뒤,
 * 그 eventId를 채운 페이로드로 다시 직렬화해 저장한다 — 페이로드 안의 eventId가
 * 행 PK와 항상 같아야 컨슈머 멱등성 키로 쓸 수 있기 때문이다.
 *
 * <p>직렬화는 Spring Boot가 구성한 {@link ObjectMapper} 빈을 주입받아 쓴다
 * (JavaTimeModule 등록 + WRITE_DATES_AS_TIMESTAMPS 비활성이 이미 돼 있어
 * LocalDateTime이 {@code "2026-07-29T12:34:56"} 형태로 나간다). 직접 {@code new
 * ObjectMapper()}를 만들면 이 설정이 빠져 타임스탬프 숫자로 직렬화된다.
 */
@Component
class OutboxAppenderImpl implements OutboxAppender {

    private static final String AGGREGATE_TYPE_ORDER = "ORDER";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    OutboxAppenderImpl(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void appendOrderConfirmed(OrderConfirmedEvent event) {
        // 1) 채번을 위해 우선 저장한다. payload는 NOT NULL이라 자리표시자를 넣고,
        //    같은 트랜잭션 안에서 아래 update로 실제 값을 채운다 — 트랜잭션 밖에서는
        //    보이지 않으므로 자리표시자가 노출될 일이 없다.
        OutboxEvent outboxEvent = new OutboxEvent(AGGREGATE_TYPE_ORDER, event.orderId(),
                event.eventType(), "{}", LocalDateTime.now());
        outboxEvent = repository.saveAndFlush(outboxEvent);

        OrderConfirmedEvent withEventId = new OrderConfirmedEvent(event.version(), outboxEvent.getId(),
                event.eventType(), event.orderId(), event.memberId(), event.orderNo(),
                event.confirmedAt(), event.lines());
        try {
            outboxEvent.setPayload(objectMapper.writeValueAsString(withEventId));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("아웃박스 이벤트 직렬화 실패: orderId=" + event.orderId(), e);
        }
        repository.save(outboxEvent);
    }
}

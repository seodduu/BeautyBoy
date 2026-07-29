package com.beautyboy.ranking;

import com.beautyboy.outbox.IdempotencyGate;
import com.beautyboy.outbox.KafkaConsumerConfig;
import com.beautyboy.outbox.OrderConfirmedEvent;
import com.beautyboy.outbox.OutboxRelay;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * sales-aggregation 컨슈머(A5). 확정된 주문의 수량만큼 일별 판매 집계를 증분한다.
 *
 * <p>A4b가 confirm 트랜잭션 안에서 하던 {@code upsertSalesIncrement} 호출을 그대로 옮겨 왔다.
 *
 * <p><b>여기만 처리 기록 테이블을 쓰는 이유</b>: 장바구니 삭제와 알림 INSERT는 두 번 해도 결과가
 * 같지만(자연 멱등 / 유니크 제약), 증분은 두 번 하면 값이 그만큼 틀어진다. at-least-once 배달에서
 * 중복 소비는 사고가 아니라 정상 경로(재시도·리밸런싱·릴레이 재발행)이므로 방어가 필요하다.
 *
 * <p>그룹 이름이 컨슈머마다 다른 이유는 {@link KafkaConsumerConfig} 클래스 주석에 있다.
 *
 * <p><b>outbox와의 경계</b>: 처리 기록은 {@link IdempotencyGate} 인터페이스로만 만진다.
 * 여기서 {@code ProcessedEvent} 엔티티나 그 리포지토리를 직접 import하면 "패키지 = 서비스 경계,
 * 타 도메인 엔티티/리포지토리 직접 import 금지"(CLAUDE.md)를 어기는 것이고, 실제로 그렇게
 * 돼 있던 것을 되돌린 결과가 지금 형태다. 계약 타입({@link OrderConfirmedEvent})은 공유
 * 메시지 계약이라 그대로 쓴다.
 */
@Component
public class SalesAggregationConsumer {

    private final IdempotencyGate idempotencyGate;
    private final GoodsDailyStatRepository goodsDailyStatRepository;
    private final ObjectMapper objectMapper;

    public SalesAggregationConsumer(IdempotencyGate idempotencyGate,
                                    GoodsDailyStatRepository goodsDailyStatRepository,
                                    ObjectMapper objectMapper) {
        this.idempotencyGate = idempotencyGate;
        this.goodsDailyStatRepository = goodsDailyStatRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 멱등성 게이트와 집계는 <b>한 트랜잭션</b>이다 — "기록됐는데 집계 안 됨" 또는 그 반대가 없다.
     *
     * <p>계획서의 판단 코드는 {@code save()} 후 {@code DataIntegrityViolationException}을 잡는
     * 형태였는데, 그 문서가 스스로 지목한 함정(유니크 위반이 트랜잭션을 롤백-only로 만든다)을
     * 피하려고 <b>네이티브 INSERT + 영향 행 수 0이면 스킵</b>으로 바꿨다. 이유는
     * {@code ProcessedEventRepository.insertIfAbsent}에 적어 두었다.
     */
    @KafkaListener(topics = OutboxRelay.TOPIC,
            groupId = KafkaConsumerConfig.GROUP_SALES_AGGREGATION,
            autoStartup = KafkaConsumerConfig.AUTO_STARTUP)
    @Transactional
    public void on(ConsumerRecord<String, String> record) {
        OrderConfirmedEvent event = 역직렬화(record.value());

        boolean 처음_보는_이벤트 = idempotencyGate.markProcessed(
                event.eventId(), IdempotencyGate.CONSUMER_SALES_AGGREGATION);
        if (!처음_보는_이벤트) {
            return;     // 이미 반영한 이벤트다. 중복 소비는 정상 동작이므로 조용히 스킵한다.
        }

        // 같은 상품이 여러 줄로 나뉘어 올 수 있으므로 줄마다 더한다(덮어쓰기가 아니다).
        for (OrderConfirmedEvent.Line line : event.lines()) {
            goodsDailyStatRepository.upsertSalesIncrement(
                    line.goodsId(), event.confirmedAt().toLocalDate(), line.quantity());
        }
    }

    /** Spring Boot가 구성한 ObjectMapper를 쓴다 — 직접 만들면 LocalDateTime 파싱이 깨진다. */
    private OrderConfirmedEvent 역직렬화(String payload) {
        try {
            return objectMapper.readValue(payload, OrderConfirmedEvent.class);
        } catch (Exception e) {
            throw new IllegalStateException("확정 이벤트 역직렬화 실패: " + payload, e);
        }
    }
}

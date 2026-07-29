package com.beautyboy.ranking;

import com.beautyboy.outbox.IdempotencyGate;
import com.beautyboy.outbox.KafkaConsumerConfig;
import com.beautyboy.outbox.OrderCanceledEvent;
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

    private static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    private static final String ORDER_CANCELED = "ORDER_CANCELED";

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
        // 같은 토픽에 ORDER_CONFIRMED와 ORDER_CANCELED가 함께 실린다(설계 §8).
        // 이 컨슈머만 둘 다 소비한다 — 판매는 확정에 늘고 취소에 줄어야 하기 때문이다.
        String eventType = 이벤트타입(record.value());
        if (ORDER_CANCELED.equals(eventType)) {
            취소를_반영한다(record.value());
            return;
        }
        if (!ORDER_CONFIRMED.equals(eventType)) {
            return;     // 모르는 타입은 이 컨슈머 소관이 아니다.
        }

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

    /**
     * 취소는 <b>취소일 기준 음수 upsert</b>다(§2 결정 6). 원 판매일을 역추적해 그 날짜를 깎지
     * 않는 이유: 랭킹은 최근 구간 가중합이라 취소를 최신 신호로 반영하는 것이 의도에 맞고,
     * 원 판매일을 찾으려면 주문을 되짚어야 해서 이벤트만으로 처리할 수 없게 된다.
     *
     * <p>멱등 게이트는 확정과 같은 상수를 쓴다 — eventId가 다르므로 서로 충돌하지 않는다.
     */
    private void 취소를_반영한다(String payload) {
        OrderCanceledEvent event = 취소_역직렬화(payload);

        boolean 처음_보는_이벤트 = idempotencyGate.markProcessed(
                event.eventId(), IdempotencyGate.CONSUMER_SALES_AGGREGATION);
        if (!처음_보는_이벤트) {
            return;
        }

        for (OrderCanceledEvent.Line line : event.lines()) {
            goodsDailyStatRepository.upsertSalesIncrement(
                    line.goodsId(), event.canceledAt().toLocalDate(), -line.quantity());
        }
    }

    private String 이벤트타입(String payload) {
        try {
            return objectMapper.readTree(payload).path("eventType").asText();
        } catch (Exception e) {
            throw new IllegalStateException("이벤트 타입 판독 실패: " + payload, e);
        }
    }

    private OrderCanceledEvent 취소_역직렬화(String payload) {
        try {
            return objectMapper.readValue(payload, OrderCanceledEvent.class);
        } catch (Exception e) {
            throw new IllegalStateException("취소 이벤트 역직렬화 실패: " + payload, e);
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

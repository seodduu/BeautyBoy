package com.beautyboy.cart;

import com.beautyboy.outbox.KafkaConsumerConfig;
import com.beautyboy.outbox.OrderConfirmedEvent;
import com.beautyboy.outbox.OutboxRelay;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * cart-clear 컨슈머(A5). 확정된 주문에 담긴 상품을 그 회원 장바구니에서 뺀다.
 *
 * <p>A4b가 {@code PaymentService.confirm} 트랜잭션 안에서 하던 일과 <b>정확히 같은 호출</b>이다 —
 * 로직을 다시 쓰지 않고 실행 위치만 옮겼다. 그래서 결제 응답은 이제 이 일을 기다리지 않고,
 * 이 일이 실패해도 결제는 롤백되지 않는다. 손님에게는 몇 초간 주문한 상품이 장바구니에 남아
 * 보일 수 있다 — 설계 §5가 명시한 <b>의도된 최종 일관성</b>이다.
 *
 * <p><b>멱등성</b>: 없는 항목을 지우는 것은 no-op이라 자연 멱등이다. 그래서 이 컨슈머만
 * {@code processed_event} 기록을 쓰지 않는다(설계 §5 — 전부에 쓰는 것은 과설계).
 *
 * <p>그룹 이름이 컨슈머마다 다른 이유는 {@link KafkaConsumerConfig} 클래스 주석에 있다
 * (설계 §5의 "그룹 하나" 기술과 다른 결정이다).
 */
@Component
public class CartClearOnOrderConfirmed {

    private static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";

    private final CartService cartService;
    private final ObjectMapper objectMapper;

    public CartClearOnOrderConfirmed(CartService cartService, ObjectMapper objectMapper) {
        this.cartService = cartService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = OutboxRelay.TOPIC,
            groupId = KafkaConsumerConfig.GROUP_CART_CLEAR,
            autoStartup = KafkaConsumerConfig.AUTO_STARTUP)
    public void on(ConsumerRecord<String, String> record) {
        // 같은 토픽에 ORDER_CANCELED도 함께 실린다(설계 §8). 취소로 장바구니를 비우면 안 된다.
        if (!ORDER_CONFIRMED.equals(이벤트타입(record.value()))) {
            return;
        }

        OrderConfirmedEvent event = 역직렬화(record.value());

        // 상품 단위로 지운다(옵션 단위가 아니다). 같은 상품을 여러 줄로 주문했으면 한 번만 부른다.
        List<Long> goodsIds = event.lines().stream()
                .map(OrderConfirmedEvent.Line::goodsId)
                .distinct()
                .toList();
        cartService.removeByGoods(event.memberId(), goodsIds);
    }


    /** 타입 판독은 역직렬화보다 먼저다 — 남의 타입 페이로드를 우리 record로 읽으려 들지 않는다. */
    private String 이벤트타입(String payload) {
        try {
            return objectMapper.readTree(payload).path("eventType").asText();
        } catch (Exception e) {
            throw new IllegalStateException("이벤트 타입 판독 실패: " + payload, e);
        }
    }

    /**
     * Spring Boot가 구성한 {@link ObjectMapper}를 쓴다 — 직접 {@code new ObjectMapper()}를 만들면
     * JavaTimeModule이 빠져 {@code confirmedAt} 파싱이 깨진다(발행 측 {@code OutboxAppender}와 같은 이유).
     */
    private OrderConfirmedEvent 역직렬화(String payload) {
        try {
            return objectMapper.readValue(payload, OrderConfirmedEvent.class);
        } catch (Exception e) {
            // 재시도해도 절대 성공하지 않는 실패다. 그래도 예외로 올려 DLT까지 보낸다 —
            // 조용히 버리면 깨진 이벤트가 있었다는 사실 자체가 사라진다.
            throw new IllegalStateException("확정 이벤트 역직렬화 실패: " + payload, e);
        }
    }
}

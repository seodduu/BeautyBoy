package com.beautyboy.notification;

import com.beautyboy.outbox.KafkaConsumerConfig;
import com.beautyboy.outbox.OrderConfirmedEvent;
import com.beautyboy.outbox.OutboxRelay;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * notification 컨슈머(A5). 결제 완료 알림을 적재한다(발송은 스코프 밖 — 설계 §2-4).
 *
 * <p>A4b가 confirm 트랜잭션 안에서 하던 {@code insertIfAbsent} 호출을 그대로 옮겨 왔다.
 * 달라진 것 하나: dedup 키로 <b>소비한 이벤트의 실제 eventId</b>를 쓴다. 동기 경로는 아웃박스 행 id를
 * 돌려받지 못해 orderId로 대신했지만, 컨슈머는 페이로드에서 진짜 값을 받는다.
 *
 * <p><b>멱등성</b>은 {@code uk_notification_dedup (member_id, event_id)}가 DB에서 막는다.
 * 애플리케이션 조건문("있으면 건너뛴다")이 아닌 이유는 동시에 두 소비가 들어오면 조회-후-INSERT가
 * 둘 다 통과하기 때문이다. 중복이면 예외가 아니라 no-op이어야 하므로 INSERT ... ON DUPLICATE KEY다.
 *
 * <p>그룹 이름이 컨슈머마다 다른 이유는 {@link KafkaConsumerConfig} 클래스 주석에 있다.
 */
@Component
public class NotificationConsumer {

    /** 알림 종류. 지금은 이 한 종류뿐이다(V92 DDL의 type 컬럼 주석과 같은 값). */
    private static final String TYPE_ORDER_CONFIRMED = "ORDER_CONFIRMED";

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(NotificationRepository notificationRepository, ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = OutboxRelay.TOPIC,
            groupId = KafkaConsumerConfig.GROUP_NOTIFICATION,
            autoStartup = KafkaConsumerConfig.AUTO_STARTUP)
    @Transactional
    public void on(ConsumerRecord<String, String> record) {
        // 같은 토픽에 ORDER_CANCELED도 함께 실린다(설계 §8). 취소에 "결제 완료" 알림을 보내면 안 된다.
        if (!TYPE_ORDER_CONFIRMED.equals(이벤트타입(record.value()))) {
            return;
        }

        OrderConfirmedEvent event = 역직렬화(record.value());

        notificationRepository.insertIfAbsent(
                event.memberId(),
                event.eventId(),
                TYPE_ORDER_CONFIRMED,
                "주문 " + event.orderNo() + " 결제가 완료됐어요.",
                LocalDateTime.now());
    }


    /** 타입 판독은 역직렬화보다 먼저다 — 남의 타입 페이로드를 우리 record로 읽으려 들지 않는다. */
    private String 이벤트타입(String payload) {
        try {
            return objectMapper.readTree(payload).path("eventType").asText();
        } catch (Exception e) {
            throw new IllegalStateException("이벤트 타입 판독 실패: " + payload, e);
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

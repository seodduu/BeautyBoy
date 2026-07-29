package com.beautyboy.ranking;

import com.beautyboy.cart.CartClearOnOrderConfirmed;
import com.beautyboy.notification.NotificationConsumer;
import com.beautyboy.notification.NotificationRepository;
import com.beautyboy.outbox.OrderCanceledEvent;
import com.beautyboy.outbox.OrderConfirmedEvent;
import com.beautyboy.outbox.OutboxRelay;
import com.beautyboy.outbox.ProcessedEventRepository;
import com.beautyboy.support.TestPersistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 취소 이벤트의 판매 집계 감소와, 같은 토픽을 공유하는 컨슈머들의 타입 가드 검증(설계 §8).
 *
 * <p>ORDER_CONFIRMED와 ORDER_CANCELED가 <b>한 토픽</b>에 실리므로, 확정만 소비하는 컨슈머는
 * 남의 타입을 스스로 걸러야 한다. 가드가 없으면 취소 이벤트로 장바구니를 비우고 "결제가
 * 완료됐어요" 알림을 보낸다.
 *
 * <p>브로커 없이 컨슈머 메서드를 직접 호출한다({@code PostOrderConsumersTest}와 같은 관례).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SalesAggregationCancelTest {

    private static final Long 회원 = 780102L;
    private static final Long 상품 = 55_001L;
    private static final Long 옵션 = 66_001L;
    /** 확정일과 취소일을 일부러 다르게 둔다 — 감소가 취소일에 찍히는지 날짜별로 갈라 본다. */
    private static final LocalDate 확정일 = LocalDate.now().minusDays(3);
    private static final LocalDate 취소일 = LocalDate.now();

    @Autowired
    SalesAggregationConsumer salesAggregationConsumer;
    @Autowired
    CartClearOnOrderConfirmed cartClearOnOrderConfirmed;
    @Autowired
    NotificationConsumer notificationConsumer;
    @Autowired
    GoodsDailyStatRepository goodsDailyStatRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    ProcessedEventRepository processedEventRepository;
    @Autowired
    ObjectMapper objectMapper;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("취소 이벤트는 취소일 기준 음수로 집계된다 — 원 판매일은 그대로 둔다")
    void 취소이벤트는_취소일에_음수로_집계된다() throws Exception {
        salesAggregationConsumer.on(레코드(확정_이벤트(1L, 2)));
        salesAggregationConsumer.on(레코드(취소_이벤트(2L, 1)));

        TestPersistence.DB_왕복_강제(entityManager);

        // 확정일에는 +2가 그대로 남는다. 취소를 원 판매일로 역추적하지 않는 것이 §2 결정 6이다.
        assertThat(판매량(확정일)).isEqualTo(2);
        // 취소일에는 -1이 새로 찍힌다. 랭킹은 최근 구간 가중합이라 취소가 최신 신호로 반영된다.
        assertThat(판매량(취소일)).isEqualTo(-1);
    }

    @Test
    @DisplayName("같은 취소 이벤트를 중복 소비해도 한 번만 반영된다")
    void 같은_취소이벤트_중복소비는_한번만_반영된다() throws Exception {
        ConsumerRecord<String, String> 취소 = 레코드(취소_이벤트(7L, 2));

        salesAggregationConsumer.on(취소);
        salesAggregationConsumer.on(취소);

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(판매량(취소일)).isEqualTo(-2);
    }

    @Test
    @DisplayName("확정과 취소는 eventId가 달라 멱등 게이트에서 서로를 막지 않는다")
    void 확정과_취소는_서로를_막지_않는다() throws Exception {
        salesAggregationConsumer.on(레코드(확정_이벤트(11L, 3)));
        salesAggregationConsumer.on(레코드(취소_이벤트(12L, 3)));

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(판매량(확정일)).isEqualTo(3);
        assertThat(판매량(취소일)).isEqualTo(-3);
    }

    @Test
    @DisplayName("장바구니·알림 컨슈머는 취소 이벤트를 스킵한다 — 게이트 행도 만들지 않는다")
    void 장바구니_알림_컨슈머는_취소이벤트를_스킵한다() throws Exception {
        long 처리기록_before = processedEventRepository.count();
        long 알림_before = notificationRepository.count();

        ConsumerRecord<String, String> 취소 = 레코드(취소_이벤트(21L, 1));
        cartClearOnOrderConfirmed.on(취소);
        notificationConsumer.on(취소);

        TestPersistence.DB_왕복_강제(entityManager);

        // "결제가 완료됐어요" 알림이 취소에 붙으면 안 된다.
        assertThat(notificationRepository.count()).isEqualTo(알림_before);
        // 남의 타입 이벤트로 게이트 행을 만들면, 나중에 그 eventId를 진짜 처리할 때 막힌다.
        assertThat(processedEventRepository.count()).isEqualTo(처리기록_before);
    }

    private int 판매량(LocalDate 날짜) {
        return goodsDailyStatRepository.findByStatDateGreaterThanEqual(날짜).stream()
                .filter(s -> 상품.equals(s.getGoodsId()) && 날짜.equals(s.getStatDate()))
                .mapToInt(GoodsDailyStat::getSalesCount)
                .sum();
    }

    private ConsumerRecord<String, String> 레코드(String payload) {
        return new ConsumerRecord<>(OutboxRelay.TOPIC, 0, 0L, "key", payload);
    }

    private String 확정_이벤트(Long eventId, int quantity) throws Exception {
        return objectMapper.writeValueAsString(new OrderConfirmedEvent(
                1, eventId, "ORDER_CONFIRMED", 900L, 회원, "ORD-CONF",
                확정일.atTime(10, 0),
                List.of(new OrderConfirmedEvent.Line(상품, 옵션, quantity))));
    }

    private String 취소_이벤트(Long eventId, int quantity) throws Exception {
        return objectMapper.writeValueAsString(new OrderCanceledEvent(
                1, eventId, "ORDER_CANCELED", 900L, 회원, "ORD-CONF",
                취소일.atTime(11, 0), 24_100 * quantity,
                List.of(new OrderCanceledEvent.Line(상품, 옵션, quantity))));
    }

    @SuppressWarnings("unused")
    private LocalDateTime 지금() {
        return LocalDateTime.now();
    }
}

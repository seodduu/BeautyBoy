package com.beautyboy.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OutboxAppenderTest {

    @Autowired
    OutboxAppender appender;

    @Autowired
    OutboxEventRepository repository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    @Transactional
    void append는_호출자_트랜잭션에_참여해_PENDING_행을_남긴다() {
        appender.appendOrderConfirmed(이벤트(1L));

        List<OutboxEvent> events = repository.findAll();
        assertThat(events).hasSize(1);
        OutboxEvent saved = events.get(0);
        assertThat(saved.getStatus()).isEqualTo(OutboxEvent.STATUS_PENDING);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPayload())
                .contains("\"orderNo\":\"BB-20260729-0001\"")
                .contains("\"goodsId\":10")
                .contains("\"optionId\":20")
                .contains("\"quantity\":3")
                .contains("\"eventId\":" + saved.getId());
    }

    @Test
    void 호출자_트랜잭션이_롤백되면_아웃박스도_남지_않는다() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.execute(status -> {
            appender.appendOrderConfirmed(이벤트(2L));
            status.setRollbackOnly();
            return null;
        });

        assertThat(repository.findAll()).isEmpty();
    }

    private OrderConfirmedEvent 이벤트(Long orderId) {
        return new OrderConfirmedEvent(1, null, "ORDER_CONFIRMED", orderId, 100L,
                "BB-20260729-0001", LocalDateTime.of(2026, 7, 29, 12, 34, 56),
                List.of(new OrderConfirmedEvent.Line(10L, 20L, 3)));
    }
}

package com.beautyboy.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 아웃박스 릴레이 단위 테스트.
 *
 * <p><b>왜 {@code @EmbeddedKafka}가 아닌 목인가</b>: 계획서 Task A4의 스텝은 "EmbeddedKafka 또는
 * KafkaTemplate 목"이라고 열어 뒀지만, 같은 계획서의 Global Constraints가 {@code @EmbeddedKafka}를
 * 명시적으로 금지한다(기동이 느려 {@code test} 태스크를 무겁게 만든다). 제약이 이긴다 —
 * 여기서는 {@link KafkaTemplate}을 목으로 두고, 실제 브로커를 거치는 종단 검증은 A7의
 * {@code @Tag("integration")} 하나가 맡는다.
 *
 * <p>리포지토리는 진짜(H2)를 쓴다. "PUBLISHED는 다시 집지 않는다"는 성질은 목으로 확인하면
 * 동어반복이 되고, 실제 쿼리(status 조건 + created_at 정렬)가 맞는지도 함께 봐야 하기 때문이다.
 * 릴레이가 자기 트랜잭션을 열고 건별로 커밋하므로 이 테스트 클래스에는 {@code @Transactional}을
 * 붙이지 않는다 — 붙이면 릴레이의 커밋 경계가 테스트 트랜잭션에 흡수돼 검증 의미가 사라진다.
 */
@SpringBootTest
@ActiveProfiles("test")
class OutboxRelayTest {

    private static final String TOPIC = "order-events";

    @Autowired
    OutboxEventRepository repository;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    PlatformTransactionManager transactionManager;

    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxRelay relay;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository.deleteAll();
        kafkaTemplate = mock(KafkaTemplate.class);
        relay = new OutboxRelay(repository, kafkaTemplate, transactionManager, 100);
    }

    @Test
    void PENDING을_생성순으로_발행하고_PUBLISHED로_마킹한다() {
        // 저장 순서와 created_at 순서를 일부러 어긋나게 둔다 — PK 순서가 아니라
        // created_at 오름차순으로 집어야 같은 주문 안의 이벤트 순서가 보존된다.
        LocalDateTime base = LocalDateTime.of(2026, 7, 29, 12, 0);
        아웃박스(30L, base.plusSeconds(3));
        아웃박스(10L, base.plusSeconds(1));
        아웃박스(20L, base.plusSeconds(2));
        발행_성공();

        relay.relay();

        InOrder order = inOrder(kafkaTemplate);
        order.verify(kafkaTemplate).send(eq(TOPIC), eq("10"), any());
        order.verify(kafkaTemplate).send(eq(TOPIC), eq("20"), any());
        order.verify(kafkaTemplate).send(eq(TOPIC), eq("30"), any());

        ArgumentCaptor<String> payloads = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, org.mockito.Mockito.times(3)).send(eq(TOPIC), any(), payloads.capture());
        assertThat(payloads.getAllValues()).allMatch(p -> p.contains("ORDER_CONFIRMED"));

        assertThat(repository.findAll())
                .allSatisfy(event -> {
                    assertThat(event.getStatus()).isEqualTo(OutboxEvent.STATUS_PUBLISHED);
                    assertThat(event.getPublishedAt()).isNotNull();
                });
    }

    @Test
    void 발행_실패시_마킹하지_않고_배치를_중단한다() {
        LocalDateTime base = LocalDateTime.of(2026, 7, 29, 12, 0);
        아웃박스(10L, base.plusSeconds(1));
        아웃박스(20L, base.plusSeconds(2));
        // 앞 건은 실패, 뒤 건은 (혹시 발행되면) 성공하도록 둔다 — 뒤 건을 실패로 두면
        // "중단했다"와 "발행했지만 실패했다"가 구분되지 않는다.
        when(kafkaTemplate.send(eq(TOPIC), eq("10"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("브로커 다운")));
        when(kafkaTemplate.send(eq(TOPIC), eq("20"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.relay();

        verify(kafkaTemplate, never()).send(eq(TOPIC), eq("20"), any());
        assertThat(repository.findAll())
                .allSatisfy(event -> {
                    assertThat(event.getStatus()).isEqualTo(OutboxEvent.STATUS_PENDING);
                    assertThat(event.getPublishedAt()).isNull();
                });
    }

    @Test
    void PUBLISHED는_다시_발행하지_않는다() {
        LocalDateTime base = LocalDateTime.of(2026, 7, 29, 12, 0);
        OutboxEvent 이미_발행됨 = 아웃박스(10L, base.plusSeconds(1));
        이미_발행됨.markPublished(base.plusSeconds(5));
        repository.save(이미_발행됨);
        아웃박스(20L, base.plusSeconds(2));
        발행_성공();

        relay.relay();

        verify(kafkaTemplate, never()).send(eq(TOPIC), eq("10"), any());
        verify(kafkaTemplate).send(eq(TOPIC), eq("20"), any());
        assertThat(repository.findById(이미_발행됨.getId()).orElseThrow().getPublishedAt())
                .isEqualTo(base.plusSeconds(5));
    }

    @Test
    void events_enabled가_false면_릴레이_빈이_없다() {
        // 기본 프로필은 beautyboy.events.enabled=false — 이때 릴레이도, NewTopic도 뜨지 않아야
        // KafkaAdmin이 브로커 접속을 시도하지 않는다(NewTopic이 없으면 initialize가 조기 반환).
        assertThat(applicationContext.getBeanNamesForType(OutboxRelay.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(org.apache.kafka.clients.admin.NewTopic.class))
                .isEmpty();
    }

    private void 발행_성공() {
        CompletableFuture<SendResult<String, String>> done = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq(TOPIC), any(), any())).thenReturn(done);
    }

    private OutboxEvent 아웃박스(Long orderId, LocalDateTime createdAt) {
        String payload = "{\"version\":1,\"eventType\":\"ORDER_CONFIRMED\",\"orderId\":" + orderId + "}";
        return repository.save(new OutboxEvent("ORDER", orderId, "ORDER_CONFIRMED", payload, createdAt));
    }
}

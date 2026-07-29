package com.beautyboy.outbox;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DLQ 재처리 단위 테스트.
 *
 * <p><b>Global Constraints와의 충돌</b>: 계획서 Task A6 통과 조건 문장("DLT에 2건 있을 때 replay 후
 * 원 토픽에 2건이 재적재되고, 두 번째 호출은 replayed: 0")은 실제 브로커가 있어야 의미 있게
 * 확인되는 종단 시나리오다. 하지만 Global Constraints가 {@code test}는 브로커·{@code @EmbeddedKafka}
 * 없이 돌아야 한다고 못박았으므로, 이 클래스는 여기서 <b>서비스 로직</b>만 확인한다 —
 * 실제 {@link Consumer}와 {@link KafkaTemplate}을 목으로 두고 "poll된 레코드를 키 그대로
 * 재발행하고 커밋한다"와 "빈 poll이면 아무것도 안 하고 0을 반환한다"는 두 동작을 단언한다.
 * "커밋된 오프셋 덕분에 두 번째 실제 호출이 0건이 된다"는 오프셋 저장소(브로커)의 동작이므로
 * 목으로는 동어반복만 될 뿐이라 검증하지 않는다 — 그 종단 확인은 A7의
 * {@code @Tag("integration")}(Testcontainers Kafka)로 넘긴다.
 */
@SpringBootTest
@ActiveProfiles("test")
class DlqReplayServiceTest {

    private static final String DLT_TOPIC = OutboxRelay.TOPIC + ".DLT";
    private static final TopicPartition PARTITION = new TopicPartition(DLT_TOPIC, 0);

    @Autowired
    ApplicationContext applicationContext;

    @Test
    @SuppressWarnings("unchecked")
    void DLT_레코드를_원_토픽에_같은_키로_재발행하고_커밋한다() {
        Consumer<String, String> consumer = mock(Consumer.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(eq(OutboxRelay.TOPIC), any(), any()))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        ConsumerRecords<String, String> batch = 레코드묶음(
                new ConsumerRecord<>(DLT_TOPIC, 0, 100L, "10", "{\"eventId\":1}"),
                new ConsumerRecord<>(DLT_TOPIC, 0, 101L, "20", "{\"eventId\":2}"));
        when(consumer.poll(any())).thenReturn(batch, ConsumerRecords.empty());

        DlqReplayService service = new DlqReplayService(() -> consumer, kafkaTemplate);

        int replayed = service.replay();

        assertThat(replayed).isEqualTo(2);
        verify(consumer).subscribe(List.of(DLT_TOPIC));
        verify(kafkaTemplate).send(OutboxRelay.TOPIC, "10", "{\"eventId\":1}");
        verify(kafkaTemplate).send(OutboxRelay.TOPIC, "20", "{\"eventId\":2}");
        verify(consumer, times(1)).commitSync();
        verify(consumer).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 빈_poll이면_아무것도_보내지_않고_0을_반환한다() {
        Consumer<String, String> consumer = mock(Consumer.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(consumer.poll(any())).thenReturn(ConsumerRecords.empty());

        DlqReplayService service = new DlqReplayService(() -> consumer, kafkaTemplate);

        int replayed = service.replay();

        assertThat(replayed).isZero();
        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(consumer, never()).commitSync();
        verify(consumer).close();
    }

    @Test
    void events_enabled가_false면_DlqReplayService_빈이_없다() {
        // 기본 프로필은 beautyboy.events.enabled=false — OutboxRelay·KafkaConsumerConfig와
        // 같은 이유로 이 서비스도 빈을 띄우지 않아야 한다.
        assertThat(applicationContext.getBeanNamesForType(DlqReplayService.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(DlqReplayController.class)).isEmpty();
    }

    private ConsumerRecords<String, String> 레코드묶음(ConsumerRecord<String, String>... records) {
        return new ConsumerRecords<>(Map.of(PARTITION, List.of(records)));
    }
}

package com.beautyboy.outbox;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * DLQ 재처리(A6, 설계 §5). {@code order-events.DLT}를 전용 그룹으로 처음부터 읽어
 * {@code order-events}로 원 키 그대로 재발행한다.
 *
 * <h2>오프셋 전략 — "처음부터"와 "두 번째 호출 0건"을 동시에 만족시키는 방법</h2>
 * 그룹 {@link #GROUP_ID}는 <b>고정</b>이고 커밋은 {@code enable.auto.commit=false} +
 * 수동 {@code commitSync()}다. {@code auto.offset.reset=earliest}는 <b>그 그룹의 커밋된
 * 오프셋이 아직 없을 때만</b> 적용된다 — 즉 "처음부터"는 "커밋 기록이 없는 첫 실행"에서만
 * 참이다. 첫 호출은 그룹이 한 번도 커밋한 적이 없으므로 토픽 처음부터 읽고, 처리 후
 * {@code commitSync()}로 오프셋을 남긴다. 두 번째 호출은 같은 그룹이 그 커밋된 오프셋
 * 뒤부터 이어 읽으므로(= "처음부터"가 아니라 "이어서") 새 메시지가 없으면 즉시 빈 poll이고
 * {@code replayed=0}이다. 두 조건이 상충하지 않는 이유가 이것이다 — "처음부터"는 그룹의
 * 생애 최초 1회에 한정된 의미다.
 *
 * <h2>종료 조건과 poll 타임아웃</h2>
 * 빈 {@link ConsumerRecords}가 나오면 그 자리에서 종료한다(연속 두 번을 기다리지 않는다).
 * {@code KafkaConsumer#poll(Duration)}은 파티션 배정(컨슈머 그룹 조인)까지 그 타임아웃
 * 예산 안에서 끝내므로, 로컬 단일 브로커 기준으로 그룹 조인(보통 수백ms 이내)과 실제 fetch를
 * 한 번의 poll 호출 안에서 마치기에 5초면 충분히 넉넉하다 — 이보다 짧으면 조인 중에 타임아웃이
 * 끝나 있는 메시지를 놓친 것처럼 빈 poll로 오판할 수 있고, 이보다 길면 (없음이 확정된) admin
 * 요청이 그만큼 오래 매달린다. 처리할 배치가 있는 정상 경로에서는 poll 1회로 바로 데이터가
 * 오므로 실제 대기 시간은 타임아웃 상한과 무관하게 짧다.
 *
 * <h2>{@code beautyboy.events.enabled=false}일 때</h2>
 * 이 빈 자체를 띄우지 않는다({@link ConditionalOnProperty}) — {@link OutboxRelay}·
 * {@link KafkaTopicConfig}·{@link KafkaConsumerConfig}와 같은 결정이다. DLQ 재처리는
 * Kafka 파이프라인이 켜져 있을 때만 의미가 있고(꺼져 있으면 DLT 자체가 생기지 않는다),
 * 꺼진 채로 호출 가능하게 두면 브로커 없는 개발 환경에서 관리자가 실수로 호출했을 때
 * 연결 시도로 멈추는 것보다 "엔드포인트가 없다(404)"가 더 정직한 실패 모드다.
 */
@Service
@ConditionalOnProperty(name = "beautyboy.events.enabled", havingValue = "true")
public class DlqReplayService {

    static final String GROUP_ID = "dlq-replay";

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final Supplier<Consumer<String, String>> consumerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    public DlqReplayService(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                             KafkaTemplate<String, String> kafkaTemplate) {
        this(() -> new KafkaConsumer<>(consumerProperties(bootstrapServers)), kafkaTemplate);
    }

    /**
     * 테스트 전용 진입점. 실브로커 없이 목 {@link Consumer}를 주입할 수 있게 팩토리를
     * {@link Supplier}로 받는다(Global Constraints: {@code test}는 브로커 없이 돈다).
     * A7의 종단 통합 테스트는 위 public 생성자로 실제 컨텍스트에서 이 빈을 그대로 쓴다.
     */
    DlqReplayService(Supplier<Consumer<String, String>> consumerFactory, KafkaTemplate<String, String> kafkaTemplate) {
        this.consumerFactory = consumerFactory;
        this.kafkaTemplate = kafkaTemplate;
    }

    /** DLT를 끝까지 읽어 원 토픽으로 재발행한 건수를 반환한다. */
    public int replay() {
        int replayed = 0;
        try (Consumer<String, String> consumer = consumerFactory.get()) {
            consumer.subscribe(List.of(OutboxRelay.TOPIC + ".DLT"));
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
                if (records.isEmpty()) {
                    break;
                }
                for (ConsumerRecord<String, String> record : records) {
                    republish(record);
                    replayed++;
                }
                // 배치 전체 재발행 성공 후 커밋 — 중간에 실패하면 커밋 없이 예외가 올라가
                // 이번 회차분은 다음 호출에서 다시 시도된다(at-least-once).
                consumer.commitSync();
            }
        }
        return replayed;
    }

    private void republish(ConsumerRecord<String, String> record) {
        try {
            // 원 키 그대로 재발행 — 같은 주문(aggregateId)이 같은 파티션으로 돌아가야
            // 순서 보장 전제가 유지된다.
            kafkaTemplate.send(OutboxRelay.TOPIC, record.key(), record.value())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DLQ 재발행 대기 중 인터럽트: key=" + record.key(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("DLQ 재발행 실패: key=" + record.key(), e);
        }
    }

    private static Properties consumerProperties(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return props;
    }
}

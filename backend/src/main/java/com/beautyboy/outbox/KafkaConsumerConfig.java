package com.beautyboy.outbox;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * 컨슈머 공통 설정 — 실패 처리와 <b>컨슈머 그룹 이름</b>(설계 §5).
 *
 * <h2>설계 §5와 다른 결정: 그룹을 셋으로 가른다</h2>
 * 설계 §5와 계획서 코드는 "컨슈머 그룹 {@code beautyboy-post-order} <b>하나</b>에 리스너 3개"라고
 * 적었다. <b>그대로 두면 동작하지 않는다.</b> 같은 토픽을 같은 그룹으로 구독하는 컨슈머 셋은
 * 한 그룹 안에서 <b>파티션을 나눠 갖는다</b>. 파티션이 3개이므로 리스너마다 하나씩 배정되고
 * 각 메시지는 셋 중 하나에게만 간다 — 장바구니만 비워지고 집계·알림은 일어나지 않는 주문이 생긴다.
 * 후처리 3종은 팬아웃(셋 모두가 모든 메시지를 받음)이어야 하므로 그룹이 갈라져야 한다.
 *
 * <p><b>왜 "리스너 하나에 3종 순차 호출"이 아닌가</b>(대안 (a)): 그러면 단일 그룹은 지키지만
 * 한 종의 실패가 나머지를 막고, 재시도·DLT가 셋 묶음으로 간다 — 알림 INSERT가 실패했다는
 * 이유로 장바구니 비우기까지 DLT로 가고, replay하면 이미 성공한 두 종이 다시 돈다.
 * 그룹을 가르면 오프셋·재시도·DLT가 컨슈머별로 독립이라, 설계 §5 표가 <b>컨슈머별 멱등성</b>을
 * 따로 적어 둔 것과도 정합적이다(그 표는 이미 컨슈머를 독립 단위로 보고 있다).
 * 비용은 파티션당 컨슈머 커넥션이 3배가 되는 것인데, 단일 토픽·파티션 3의 규모에서는 무시할 만하다.
 *
 * <p>{@code application.yml}의 {@code spring.kafka.consumer.group-id}는 그대로 두고
 * 리스너마다 {@code groupId}를 명시해 덮어쓴다 — 애노테이션의 groupId가 우선한다.
 */
@Configuration
@ConditionalOnProperty(name = "beautyboy.events.enabled", havingValue = "true")
public class KafkaConsumerConfig {

    /** 그룹 이름의 공통 앞머리. 브로커에서 이 접두사로 후처리 컨슈머들을 한눈에 묶어 볼 수 있다. */
    private static final String GROUP_PREFIX = "beautyboy-post-order.";

    public static final String GROUP_CART_CLEAR = GROUP_PREFIX + "cart-clear";
    public static final String GROUP_SALES_AGGREGATION = GROUP_PREFIX + "sales-aggregation";
    public static final String GROUP_NOTIFICATION = GROUP_PREFIX + "notification";

    /**
     * 리스너 컨테이너 기동 토글. {@code beautyboy.events.enabled}가 켜져 있을 때만 구독을 시작한다.
     *
     * <p>컨슈머 빈 자체는 이 토글과 무관하게 항상 등록된다 — 토글이 꺼진 {@code test}에서도
     * 빈을 주입받아 메서드를 직접 부를 수 있어야 하기 때문이다(Global Constraints: 브로커·
     * EmbeddedKafka 없이 도는 테스트). 컨테이너가 뜨지 않으므로 브로커 접속은 일어나지 않는다.
     */
    public static final String AUTO_STARTUP = "${beautyboy.events.enabled:false}";

    private static final long 초기_간격_ms = 1000;
    private static final double 배수 = 2.0;
    /** 재시도 3회(1s → 2s → 4s). 소진하면 DefaultErrorHandler가 리커버러(DLT 발행)를 부른다. */
    private static final int 최대_재시도 = 3;

    /**
     * 실패 처리(설계 §5). 재시도를 소진한 레코드를 {@code order-events.DLT}로 보낸다 —
     * 원본 토픽·파티션·오프셋·예외는 헤더에 보존된다. A6의 replay가 그 헤더에 기댄다.
     *
     * <p><b>목적지를 명시하는 이유(A7 실브로커 통합 테스트가 잡은 배선 버그)</b>: 이 자리는
     * 원래 {@code new DeadLetterPublishingRecoverer(kafkaTemplate)}(목적지 리졸버 미지정)였고,
     * 주석은 "기본 규칙이 원본 토픽 + {@code .DLT}"라고 적어 뒀다 — 그 가정이 틀렸다.
     * Spring Kafka(이 프로젝트가 쓰는 3.3.6)의 실제 기본 목적지 리졸버는 원본 토픽에
     * <b>{@code -dlt}(소문자, 하이픈)</b>를 붙인다(바이트코드로 확인: {@code DEFAULT_DESTINATION_RESOLVER}가
     * {@code "<토픽>-dlt"} 템플릿을 쓴다). 그래서 재시도를 소진한 레코드는 {@link KafkaTopicConfig}가
     * 실제로 만든 {@code order-events.DLT}가 아니라 존재하지 않는 {@code order-events-dlt}로 갔다 —
     * 브로커가 그 토픽을 몰라 프로듀서가 파티션을 스스로 정하는 경고(UNKNOWN_TOPIC_OR_PARTITION)만
     * 남기고, {@link DlqReplayService}는 처음부터 아무것도 못 읽는다. DLQ 재처리 기능 전체가
     * 실제로는 작동하지 않는 상태였다 — Testcontainers Kafka로 실브로커를 거친 A7이 처음 잡았다
     * (H2/목 기반 테스트는 목적지 리졸버 호출 자체를 검증하지 않아 이 불일치를 볼 수 없었다).
     * 그래서 목적지를 {@link KafkaTopicConfig}가 만든 실제 DLT 토픽 이름으로 명시한다.
     *
     * <p>{@code CommonErrorHandler} 빈이 하나 있으면 Spring Boot가 구성하는 리스너 컨테이너
     * 팩토리가 그것을 집어 모든 {@code @KafkaListener}에 적용한다 — 컨슈머마다 붙일 필요가 없다.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        return new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate, DLT_목적지_리졸버()), 재시도_백오프());
    }

    /** {@link KafkaTopicConfig}가 실제로 만드는 DLT 토픽의 접미사. 이것과 어긋나면 DLQ가 통째로 죽는다. */
    static final String DLT_접미사 = ".DLT";

    /**
     * DLT 목적지 결정 규칙: 원본 토픽 + {@code .DLT}, <b>파티션은 원본 그대로</b>.
     *
     * <p><b>왜 람다를 인라인으로 두지 않고 꺼내는가</b>: 이 규칙이 바로 실제로 조용히 깨져 있던
     * 자리다(위 문단 참고). 그런데 그것을 잡아낸 것은 Docker가 필요한 {@code integrationTest}뿐이었고,
     * 흔한 개발 루프인 {@code ./gradlew test}는 이 자리를 전혀 검증하지 못했다 — 그쪽 테스트는
     * "핸들러가 null이 아니다" 수준이라 이름이 약속한 것을 지키지 못했다. 규칙을 여기로 꺼내면
     * 브로커 없이 {@code resolve(record, ex).topic()}만 불러 회귀를 잡을 수 있다.
     *
     * <p>파티션을 보존하는 이유: 원본 파티션을 유지해야 DLT에서도 같은 주문(키=orderId)의 실패
     * 레코드가 한 파티션에 순서대로 모이고, {@link DlqReplayService}의 재처리도 원본과 같은
     * 분포를 따른다.
     */
    static java.util.function.BiFunction<org.apache.kafka.clients.consumer.ConsumerRecord<?, ?>,
            Exception, TopicPartition> DLT_목적지_리졸버() {
        return (record, exception) -> new TopicPartition(record.topic() + DLT_접미사, record.partition());
    }

    /**
     * 백오프. {@code setMaxAttempts}로 재시도 횟수를 3으로 못 박는다 —
     * 이것을 두지 않으면 기본 {@code maxElapsedTime}(사실상 무한)까지 재시도해 DLT로 가지 않고,
     * 그 파티션의 뒤 메시지가 전부 막힌다.
     */
    static ExponentialBackOff 재시도_백오프() {
        ExponentialBackOff backOff = new ExponentialBackOff(초기_간격_ms, 배수);
        backOff.setMaxAttempts(최대_재시도);
        return backOff;
    }
}

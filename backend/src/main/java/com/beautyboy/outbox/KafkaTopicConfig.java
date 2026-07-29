package com.beautyboy.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 토픽 선언(설계 §5). {@code KafkaAdmin}이 이 NewTopic 빈들을 보고 기동 시 토픽을 만든다.
 *
 * <p>파티션 3의 근거: 단일 컨슈머 인스턴스에서도 파티션별 병렬 소비를 실험할 수 있는 최소 수.
 * 레플리카 1은 로컬 단일 브로커(KRaft) 구성이기 때문 — 운영이라면 3노드/replicas 3이다.
 * DLT도 같은 파티션 수로 맞춰 원본 파티션이 그대로 보존되게 한다.
 *
 * <p>{@code @ConditionalOnProperty}가 필수인 이유: NewTopic 빈이 하나라도 있으면 KafkaAdmin이
 * 기동 시 브로커 접속을 시도한다. 토글이 꺼진 로컬·테스트 환경에서 Kafka 없이 앱이 떠야 하므로
 * 빈 자체를 만들지 않는다(NewTopic이 없으면 KafkaAdmin은 접속 없이 조기 반환한다).
 */
@Configuration
@ConditionalOnProperty(name = "beautyboy.events.enabled", havingValue = "true")
public class KafkaTopicConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICAS = 1;

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(OutboxRelay.TOPIC)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    /** 컨슈머가 재시도를 소진하면 {@code DefaultErrorHandler}가 여기로 보낸다(기본 명명 규칙). */
    @Bean
    public NewTopic orderEventsDltTopic() {
        return TopicBuilder.name(OutboxRelay.TOPIC + ".DLT")
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }
}

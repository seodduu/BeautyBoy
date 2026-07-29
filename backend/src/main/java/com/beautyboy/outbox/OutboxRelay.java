package com.beautyboy.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 아웃박스 릴레이. {@code status=PENDING}인 행을 {@code created_at} 오름차순으로 폴링해
 * Kafka {@code order-events}로 발행하고 PUBLISHED로 마킹한다(설계 §4.2).
 *
 * <p><b>토글</b>: {@code beautyboy.events.enabled=false}(기본)면 이 빈도, {@link KafkaTopicConfig}의
 * NewTopic도 뜨지 않는다 — 아웃박스 INSERT만 쌓이고(무해) 앱은 Kafka 없이 뜬다.
 *
 * <p><b>트랜잭션 경계 — 건별 커밋</b>: {@code relay()} 전체를 하나의 {@code @Transactional}로
 * 감싸면 배치 도중 죽었을 때 이미 발행에 성공한 앞 건까지 PENDING으로 롤백돼, 다음 주기에
 * 전부 재발행된다(중복 폭이 배치 크기만큼 커진다). 그래서 마킹만 {@link TransactionTemplate}
 * + {@code REQUIRES_NEW}로 건별 커밋한다. {@code @Transactional} 애노테이션 대신
 * TransactionTemplate을 쓴 이유는 두 가지다 — (1) 자기 호출(self-invocation)은 프록시를 타지
 * 않아 메서드 애노테이션으로는 건별 경계를 만들 수 없고, (2) REQUIRES_NEW라 릴레이가 어떤
 * 트랜잭션 안에서 호출되더라도 마킹 커밋이 호출자 경계에 흡수되지 않는다.
 *
 * <p>폴링 조회는 트랜잭션 밖에서 일어나므로 반환된 엔티티는 준영속이다. 마킹 후
 * {@code save}가 merge로 반영한다 — 발행과 마킹 사이에 DB 커넥션을 붙들지 않는 이점도 있다.
 */
@Component
@ConditionalOnProperty(name = "beautyboy.events.enabled", havingValue = "true")
public class OutboxRelay {

    /** 설계 §5의 토픽. 키는 orderId — 같은 주문은 같은 파티션에 떨어져 순서가 보장된다. */
    public static final String TOPIC = "order-events";

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate markTransaction;
    private final int batchSize;

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       PlatformTransactionManager transactionManager,
                       @Value("${beautyboy.events.relay-batch-size:100}") int batchSize) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.markTransaction = new TransactionTemplate(transactionManager);
        this.markTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${beautyboy.events.relay-delay-ms:1000}")
    public void relay() {
        List<OutboxEvent> pending = repository.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.STATUS_PENDING, Limit.of(batchSize));
        for (OutboxEvent event : pending) {
            try {
                // key = orderId 문자열 — 같은 주문은 같은 파티션. send를 동기로 기다리는 이유:
                // 발행 성공이 확인된 것만 PUBLISHED로 마킹해야 at-least-once가 성립한다.
                kafkaTemplate.send(TOPIC, String.valueOf(event.getAggregateId()), event.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.markPublished(LocalDateTime.now());
                // 건별 커밋 — 중간에 죽으면 남은 건 다음 주기에 재발행(중복 허용)
                markTransaction.executeWithoutResult(status -> repository.save(event));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("outbox 발행 대기 중 인터럽트 — 다음 주기에 재시도. eventId={}", event.getId(), e);
                break;
            } catch (Exception e) {
                log.warn("outbox 발행 실패 — 다음 주기에 재시도. eventId={}", event.getId(), e);
                break;  // 순서 보존: 앞 건이 실패했는데 뒤 건을 발행하면 같은 주문 내 순서가 깨질 수 있다
            }
        }
    }
}

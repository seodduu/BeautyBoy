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
 *
 * <p><b>영구 실패 처리(독약 메시지)</b>: 발행 실패 시 {@code break}로 배치를 중단하는 것은
 * 같은 주문 내 순서를 지키기 위해서고, 브로커 다운 같은 <b>일시적</b> 실패에는 옳다. 그런데
 * 직렬화 불가·{@code RecordTooLargeException} 같은 <b>영구</b> 실패면 그 한 건이
 * {@code created_at} 최선두에 영원히 남아 뒤의 모든 주문 이벤트가 영영 발행되지 않는다 —
 * 결제는 성공하는데 장바구니가 안 비워지고 집계도 알림도 멈추며, 남는 신호는 초당 warn 로그
 * 한 줄뿐이다. 컨슈머 실패에는 DLT가 있는데 발행 실패에만 대응 장치가 없던 비대칭이었다.
 * 그래서 시도 횟수를 세어 {@code beautyboy.events.relay-max-attempts}(기본 10)에 도달하면
 * {@link OutboxEvent#STATUS_FAILED}로 옮기고 로그를 {@code error}로 승격한다(V93).
 * 발행 실패는 DLT로 보낼 수 없다 — 애초에 브로커에 넣지 못한 것이므로 DLT에 해당하는 자리는
 * DB의 FAILED 상태다.
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
    private final int maxAttempts;

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       PlatformTransactionManager transactionManager,
                       @Value("${beautyboy.events.relay-batch-size:100}") int batchSize,
                       @Value("${beautyboy.events.relay-max-attempts:10}") int maxAttempts) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.markTransaction = new TransactionTemplate(transactionManager);
        this.markTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
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
                // 인터럽트는 이 행의 잘못이 아니라 스레드 종료 신호다 — 시도 횟수를 세지 않는다.
                log.warn("outbox 발행 대기 중 인터럽트 — 다음 주기에 재시도. eventId={}", event.getId(), e);
                break;
            } catch (Exception e) {
                발행_실패를_기록한다(event, e);
                break;  // 순서 보존: 앞 건이 실패했는데 뒤 건을 발행하면 같은 주문 내 순서가 깨질 수 있다
            }
        }
    }

    /**
     * 실패 횟수를 올려 커밋하고, 임계치에 도달했으면 FAILED로 옮긴다.
     *
     * <p>마킹과 같은 {@code REQUIRES_NEW} 경계를 쓴다 — 이 커밋이 남지 않으면 카운터가 영원히
     * 0이라 임계치에 도달하지 못하고, 결국 고치려던 무한 재시도로 되돌아간다.
     *
     * <p>기록 자체가 실패하는 경우(DB도 함께 죽은 상황)는 삼킨다. 여기서 예외를 던지면
     * {@code @Scheduled} 메서드가 중단될 뿐 얻는 것이 없고, 원래의 발행 실패 로그는 이미 남았다.
     */
    private void 발행_실패를_기록한다(OutboxEvent event, Exception cause) {
        String 사유 = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        try {
            boolean 포기했다 = event.recordFailure(사유, maxAttempts);
            markTransaction.executeWithoutResult(status -> repository.save(event));
            if (포기했다) {
                // 침묵을 깨는 자리. 이 행은 더 이상 발행되지 않으므로 사람이 반드시 봐야 한다.
                log.error("outbox 발행을 {}회 실패해 FAILED로 격리한다 — 이 이벤트의 후처리(장바구니·집계·알림)는 "
                                + "일어나지 않는다. 원인을 고친 뒤 status='PENDING', attempt_count=0으로 되돌려 재발행하라. "
                                + "eventId={}, aggregateId={}",
                        maxAttempts, event.getId(), event.getAggregateId(), cause);
            } else {
                log.warn("outbox 발행 실패({}/{}) — 다음 주기에 재시도. eventId={}",
                        event.getAttemptCount(), maxAttempts, event.getId(), cause);
            }
        } catch (Exception 기록_실패) {
            log.error("outbox 발행 실패를 기록하지도 못했다 — DB도 함께 문제일 수 있다. eventId={}",
                    event.getId(), 기록_실패);
        }
    }
}

package com.beautyboy.outbox;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * {@link IdempotencyGate}의 유일한 구현. {@code processed_event} 복합 PK가 진짜 방어선이고
 * 이 클래스는 그 결과(영향 행 수)를 boolean으로 번역할 뿐이다.
 *
 * <p>트랜잭션을 스스로 열지 않는 것이 중요하다 — 호출자(컨슈머)의 {@code @Transactional} 안에서
 * 돌아야 "기록됐는데 집계 안 됨"이나 그 반대가 생기지 않는다. 여기에 {@code REQUIRES_NEW} 같은
 * 것을 붙이면 그 원자성이 깨진다.
 */
@Component
public class ProcessedEventIdempotencyGate implements IdempotencyGate {

    private final ProcessedEventRepository repository;

    public ProcessedEventIdempotencyGate(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean markProcessed(long eventId, String consumer) {
        // 영향 행 0 == 이미 있던 행. 이 판정은 MySQL 드라이버의 useAffectedRows=true에 의존한다
        // (application.yml의 spring.datasource.hikari.data-source-properties 주석 참고).
        return repository.insertIfAbsent(eventId, consumer, LocalDateTime.now()) > 0;
    }
}

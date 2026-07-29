package com.beautyboy.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.Key> {

    /**
     * 멱등성 게이트. 처리 기록을 남기고, <b>실제로 남은 경우에만</b> 1을 돌려준다.
     * 이미 같은 {@code (event_id, consumer)}가 있으면 아무 일도 하지 않고 0이다.
     *
     * <p><b>왜 {@code save()} + {@code DataIntegrityViolationException} catch가 아닌가</b>
     * (계획서의 판단 코드에서 벗어난 지점, 그 문서가 함정으로 지목한 그 자리다):
     * JPA로 PK 충돌을 내면 그 트랜잭션은 롤백-only로 마킹된다. 예외를 잡아 {@code return}만 해도
     * 컨슈머 메서드의 {@code @Transactional}이 커밋 시점에
     * {@code UnexpectedRollbackException}을 던지고, 리스너 컨테이너는 그것을 처리 실패로 보아
     * 재시도하다 결국 <b>정상적인 중복 소비를 DLT로 보낸다</b>. 중복 소비는 오류가 아니라
     * 정상 동작(재시도·리밸런싱·릴레이 재발행)이므로 예외 자체가 나지 않아야 한다.
     * A4b의 {@link com.beautyboy.notification.NotificationRepository#insertIfAbsent}가
     * 같은 이유로 같은 모양이다.
     *
     * <p>{@code on duplicate key update event_id = event_id}는 "충돌하면 아무것도 바꾸지 마라"의
     * 관용구다. H2도 MySQL 모드에서 같은 구문을 지원하므로 테스트에서 같은 경로가 돈다.
     *
     * @return 실제로 삽입된 행 수(중복이면 0)
     */
    @Modifying
    @Query(value = "insert into processed_event (event_id, consumer, processed_at) "
            + "values (:eventId, :consumer, :processedAt) "
            + "on duplicate key update event_id = event_id", nativeQuery = true)
    int insertIfAbsent(@Param("eventId") Long eventId,
                       @Param("consumer") String consumer,
                       @Param("processedAt") LocalDateTime processedAt);
}

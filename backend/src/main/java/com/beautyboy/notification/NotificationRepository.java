package com.beautyboy.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByMemberId(Long memberId);

    /**
     * 알림 적재. 이미 같은 (member_id, event_id)가 있으면 아무 일도 하지 않는다.
     *
     * <p>왜 {@code save(new Notification(...))}가 아닌가:
     * <ul>
     *   <li><b>중복이 예외가 아니라 no-op이어야 한다.</b> 중복 소비는 정상 동작(재시도·리밸런싱)이지
     *       오류가 아니다. {@code save}로 넣으면 유니크 위반이 {@code DataIntegrityViolationException}이 되어
     *       트랜잭션이 롤백-only로 마킹되고, 잡아도 그 트랜잭션은 이미 못 쓴다.</li>
     *   <li><b>호출자가 {@link Notification} 엔티티를 import하지 않아도 된다.</b> 알림을 남기는 쪽은
     *       {@code order}(A4b)와 A5의 컨슈머인데, 타 도메인 엔티티 직접 import는 금지다(CLAUDE.md).
     *       스칼라만 받는 이 메서드는 그 경계를 넘지 않는다.</li>
     * </ul>
     *
     * <p>{@code on duplicate key update member_id = member_id}는 "충돌하면 아무것도 바꾸지 마라"의
     * 관용구다. H2도 MySQL 모드에서 같은 구문을 지원하므로 테스트에서 같은 경로가 돈다.
     *
     * @return 실제로 삽입된 행 수(중복이면 0)
     */
    @Modifying
    @Query(value = "insert into notification (member_id, event_id, type, message, created_at) "
            + "values (:memberId, :eventId, :type, :message, :createdAt) "
            + "on duplicate key update member_id = member_id", nativeQuery = true)
    int insertIfAbsent(@Param("memberId") Long memberId,
                       @Param("eventId") Long eventId,
                       @Param("type") String type,
                       @Param("message") String message,
                       @Param("createdAt") LocalDateTime createdAt);
}

package com.beautyboy.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentCompensationRepository extends JpaRepository<PaymentCompensation, Long> {

    /** 자동 재시도 대상 — PENDING_RETRY이면서 상한을 아직 안 쓴 행. */
    @Query("select c from PaymentCompensation c "
            + "where c.status = '" + PaymentCompensation.STATUS_PENDING_RETRY + "' "
            + "and c.retryCount < :maxRetry order by c.id asc")
    List<PaymentCompensation> findRetryTargets(@Param("maxRetry") int maxRetry);

    /**
     * 승격 대상 — 주어진 상태로 {@code before}보다 오래 머문 행.
     * 커밋은 ms 단위라 5분 남은 IN_FLIGHT는 커밋 실패 확정으로 봐도 오탐이 없다(설계 §5-4).
     */
    @Query("select c from PaymentCompensation c where c.status in :statuses "
            + "and c.createdAt < :before order by c.id asc")
    List<PaymentCompensation> findStale(@Param("statuses") List<String> statuses,
                                        @Param("before") LocalDateTime before);

    /** 상태 목록 조회(admin). status가 null이면 전체 — created_at 내림차순. */
    @Query("select c from PaymentCompensation c "
            + "where (:status is null or c.status = :status) order by c.createdAt desc, c.id desc")
    List<PaymentCompensation> findForAdmin(@Param("status") String status);

    /**
     * 상태 갱신 벌크 UPDATE. {@link CompensationRecorder}가 REQUIRES_NEW 안에서 쓴다 —
     * 엔티티를 읽어 더티체킹으로 바꾸면 롤백되는 바깥 영속성 컨텍스트와 얽힌다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PaymentCompensation c set c.status = :status, c.lastError = :lastError, "
            + "c.resolvedAt = :resolvedAt where c.id = :id")
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("lastError") String lastError,
                     @Param("resolvedAt") LocalDateTime resolvedAt);
}

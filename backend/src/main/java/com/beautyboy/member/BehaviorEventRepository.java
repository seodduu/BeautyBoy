package com.beautyboy.member;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface BehaviorEventRepository extends JpaRepository<BehaviorEvent, Long> {

    long countByMemberId(Long memberId);

    /**
     * threshold보다 오래된 행을 batchSize만큼만 지운다. JPQL DELETE에는 LIMIT이 없어 네이티브
     * 쿼리를 쓴다. {@code idx_behavior_event_time (created_at)}이 이 범위 스캔을 받는다.
     *
     * <p>{@code @Transactional}을 여기 둔 이유: {@code BehaviorEventRetentionJob.purge()}가
     * 이 메서드를 반복 호출해 반복 하나를 독립 트랜잭션으로 끊으려 한다. 호출자 쪽(자기 클래스
     * 안의 다른 메서드)에 어노테이션을 두면 자기 호출이라 스프링 프록시 어드바이스를 타지
     * 않는다. 리포지토리 인터페이스 메서드는 스프링 데이터가 만든 프록시를 거쳐 호출되므로
     * 여기 두어야 실제로 매 호출이 자기 트랜잭션을 갖는다.
     */
    @Transactional
    @Modifying
    @Query(value = "DELETE FROM behavior_event WHERE created_at < :threshold LIMIT :batchSize",
           nativeQuery = true)
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold, @Param("batchSize") int batchSize);
}

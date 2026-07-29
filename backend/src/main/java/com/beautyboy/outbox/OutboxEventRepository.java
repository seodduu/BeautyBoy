package com.beautyboy.outbox;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** 릴레이(A4)가 PENDING을 생성순으로 배치 폴링할 때 쓴다. */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status, Limit limit);
}

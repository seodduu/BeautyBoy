package com.beautyboy.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderCancelRepository extends JpaRepository<OrderCancel, Long> {

    /** 한 주문의 취소 회차를 오래된 순으로. 상세 화면의 이력 표시와 회차 검증이 쓴다. */
    List<OrderCancel> findByOrderIdOrderByIdAsc(Long orderId);
}

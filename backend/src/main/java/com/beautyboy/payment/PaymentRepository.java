package com.beautyboy.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByOrderId(Long orderId);

    /** 주문 1건당 결제 1건(uk_payment_order, V32)이라 Optional로 충분하다. 취소가 paymentKey를 찾는 통로. */
    Optional<Payment> findByOrderId(Long orderId);
}

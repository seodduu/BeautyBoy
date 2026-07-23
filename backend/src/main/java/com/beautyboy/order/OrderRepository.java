package com.beautyboy.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    List<Order> findByMemberIdOrderByOrderedAtDesc(Long memberId);

    boolean existsByOrderNo(String orderNo);

    /**
     * 결제 승인용 비관적 락 조회.
     *
     * <p>같은 주문에 승인 요청이 동시에 두 번 들어오면(사용자 더블클릭·재시도)
     * 둘 다 "결제대기"를 읽고 둘 다 승인 처리해 이중 청구가 된다.
     * 행을 잠가 한 번에 하나만 상태 전이를 시도하게 만든다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderNo = :orderNo")
    Optional<Order> findByOrderNoForUpdate(@Param("orderNo") String orderNo);
}

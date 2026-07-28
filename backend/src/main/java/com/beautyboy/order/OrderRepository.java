package com.beautyboy.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    List<Order> findByMemberIdOrderByOrderedAtDesc(Long memberId);

    /**
     * 내 주문 한 페이지. 2차 정렬 키로 id desc를 두는 이유: ordered_at만으로 정렬하면 같은 초에
     * 만들어진 주문 두 건의 순서가 비결정적이고, 그러면 페이지 경계에서 한 건이 사라지거나
     * 두 번 나온다. (findRecommendedRows·findCandidateIds가 이미 같은 이유로 2차 키를 둔다.)
     */
    Page<Order> findByMemberIdOrderByOrderedAtDescIdDesc(Long memberId, Pageable pageable);

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

    /** 이 회원의 status 상태 주문 중 goodsNo를 담은 것이 존재하는가. 구매인증용. */
    @Query("select count(i) > 0 from Order o join o.items i "
            + "where o.memberId = :memberId and i.goodsId = :goodsNo and o.status = :status")
    boolean existsPaidItem(@Param("memberId") Long memberId,
                           @Param("goodsNo") Long goodsNo,
                           @Param("status") String status);
}

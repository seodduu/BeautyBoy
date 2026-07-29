package com.beautyboy.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoodsOptionRepository extends JpaRepository<GoodsOption, Long> {

    /**
     * 조건부 차감. 영향 행 1 = 확보, 0 = 재고 부족. 이 한 문장이 검증과 차감을 원자로 묶어
     * "확인한 재고를 남이 먼저 가져가는" 틈을 없앤다. 낙관적 락 예외에 의존하지 않는 이유는
     * 계획서 §2 결정 4.
     *
     * <p><b>clearAutomatically를 켜지 않는다.</b> 이 쿼리는 confirm 트랜잭션 중간에 불리는데,
     * 영속성 컨텍스트를 비우면 락과 함께 읽어 둔 Order가 detach되어 뒤따르는
     * {@code markPaid()} 변경이 조용히 유실된다(더티체킹 대상에서 빠진다). 이 트랜잭션은
     * GoodsOption 엔티티를 읽지 않으므로 1차 캐시가 낡을 일도 없다.
     * 회귀 방어: PaymentStockConfirmTest의 "차감 뒤에도 결제 완료 전이가 유실되지 않는다".
     */
    @Modifying(flushAutomatically = true)
    @Query("update GoodsOption o set o.stock = o.stock - :qty "
            + "where o.id = :optionId and o.stock >= :qty")
    int deduct(@Param("optionId") Long optionId, @Param("qty") int qty);

    /** 조건 없는 원자 증가. deduct와 같은 flushAutomatically 근거(락 조회 엔티티 detach 방지). */
    @Modifying(flushAutomatically = true)
    @Query("update GoodsOption o set o.stock = o.stock + :qty where o.id = :optionId")
    int restore(@Param("optionId") Long optionId, @Param("qty") int qty);
}

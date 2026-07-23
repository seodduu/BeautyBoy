package com.beautyboy.order;

import com.beautyboy.ranking.SalesStatProvider;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 랭킹이 요구하는 판매 통계를 order가 공급한다(의존성 역전).
 *
 * <p>ranking은 order 테이블을 직접 읽을 수 없다(패키지 = 서비스 경계). 그래서 ranking이 인터페이스를
 * 정의하고 데이터를 가진 order가 구현한다. 이 {@code @Component}가 존재하면
 * ranking의 빈 맵 폴백({@code RankingStatFallbackAutoConfiguration})이 자동으로 물러난다.
 *
 * <p>결제완료(PAID) 주문만 센다 — 장바구니에 담아두기만 해도 랭킹이 오르면 조작이 쉬워진다.
 */
@Component
public class OrderSalesStatProvider implements SalesStatProvider {

    private final EntityManager em;

    public OrderSalesStatProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> salesQuantityByGoods(LocalDate date) {
        // 그 날 결제완료된 주문의 상품별 수량 합. paid_at 기준으로 하루 경계를 잡는다.
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        List<Object[]> rows = em.createQuery(
                        "select i.goodsId, sum(i.quantity) from Order o join o.items i "
                                + "where o.status = :paid and o.paidAt >= :from and o.paidAt < :to "
                                + "group by i.goodsId", Object[].class)
                .setParameter("paid", Order.STATUS_PAID)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }
}

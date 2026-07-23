package com.beautyboy.order;

import com.beautyboy.ranking.SalesStatProvider;
import com.beautyboy.support.TestPersistence;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderSalesStatProviderTest {

    @Autowired
    SalesStatProvider salesStatProvider;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    EntityManager entityManager;

    @Test
    void 폴백이_아니라_주문_도메인_구현이_주입된다() {
        // 이 구현이 있으면 ranking의 빈 맵 폴백이 물러나야 한다.
        // 여기가 깨지면 랭킹이 조용히 조회수 랭킹으로 남는다.
        assertThat(salesStatProvider).isInstanceOf(OrderSalesStatProvider.class);
    }

    @Test
    void 그_날_결제완료된_주문의_상품별_판매수량을_센다() {
        결제완료_주문(1L, 2, LocalDateTime.now());
        결제완료_주문(1L, 3, LocalDateTime.now());
        결제완료_주문(2L, 1, LocalDateTime.now());

        Map<Long, Integer> result = salesStatProvider.salesQuantityByGoods(LocalDate.now());

        assertThat(result).containsEntry(1L, 5).containsEntry(2L, 1);
    }

    @Test
    void 결제대기_주문은_세지_않는다() {
        // 담아두기만 해도 랭킹이 오르면 조작이 쉬워진다. 결제완료만 센다.
        결제대기_주문(1L, 10, LocalDateTime.now());

        assertThat(salesStatProvider.salesQuantityByGoods(LocalDate.now())).isEmpty();
    }

    @Test
    void 다른_날_결제는_세지_않는다() {
        결제완료_주문(1L, 5, LocalDateTime.now().minusDays(1));

        assertThat(salesStatProvider.salesQuantityByGoods(LocalDate.now())).isEmpty();
    }

    private void 결제완료_주문(Long goodsId, int quantity, LocalDateTime paidAt) {
        Order order = 주문(goodsId, quantity);
        order.markPaid(paidAt);
        orderRepository.saveAndFlush(order);
        TestPersistence.DB_왕복_강제(entityManager);
    }

    private void 결제대기_주문(Long goodsId, int quantity, LocalDateTime orderedAt) {
        orderRepository.saveAndFlush(주문(goodsId, quantity));
        TestPersistence.DB_왕복_강제(entityManager);
    }

    private Order 주문(Long goodsId, int quantity) {
        Order order = new Order("ORD-" + System.nanoTime(), 1L, "홍길동", "010-0000-0000",
                "06234", "서울시", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(goodsId, null, "상품", null, 10000, quantity));
        return order;
    }
}

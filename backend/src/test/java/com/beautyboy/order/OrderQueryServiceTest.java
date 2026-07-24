package com.beautyboy.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 구매인증 통로 테스트. 리뷰가 "산 사람만 쓴다"를 판정하는 유일한 근거다.
 * order 테이블을 review가 직접 못 보므로 이 인터페이스로만 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderQueryServiceTest {

    private static final Long 회원 = 1L;
    private static final Long 상품 = 100L;

    @Autowired
    OrderQueryService orderQueryService;
    @Autowired
    OrderRepository orderRepository;

    @Test
    void 결제완료_주문에_그_상품이_있으면_구매로_인정한다() {
        결제완료_주문(회원, 상품);

        assertThat(orderQueryService.hasPurchased(회원, 상품)).isTrue();
    }

    @Test
    void 결제대기_주문만_있으면_구매가_아니다() {
        // 담아두고 결제 안 한 것으로 리뷰를 쓸 수 있으면 인증이 무의미하다.
        결제대기_주문(회원, 상품);

        assertThat(orderQueryService.hasPurchased(회원, 상품)).isFalse();
    }

    @Test
    void 다른_회원의_구매는_내_구매가_아니다() {
        결제완료_주문(999L, 상품);

        assertThat(orderQueryService.hasPurchased(회원, 상품)).isFalse();
    }

    @Test
    void 산_적_없는_상품이면_구매가_아니다() {
        결제완료_주문(회원, 상품);

        assertThat(orderQueryService.hasPurchased(회원, 200L)).isFalse();
    }

    private void 결제완료_주문(Long memberId, Long goodsId) {
        Order order = 주문(memberId, goodsId);
        order.markPaid(LocalDateTime.now());
        orderRepository.saveAndFlush(order);
    }

    private void 결제대기_주문(Long memberId, Long goodsId) {
        orderRepository.saveAndFlush(주문(memberId, goodsId));
    }

    private Order 주문(Long memberId, Long goodsId) {
        Order order = new Order("ORD-" + System.nanoTime(), memberId, "홍길동", "010-0000-0000",
                "06234", "서울시", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(goodsId, null, "상품", null, 10000, 1));
        return order;
    }
}

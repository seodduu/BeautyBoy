package com.beautyboy.order;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 결제 승인이 주문에 요구하는 것(order 소유 경계)의 계약 검증.
 *
 * <p>클래스 {@code @Transactional}이 곧 호출자의 트랜잭션 역할을 한다 — 구현이 요구하는
 * {@code Propagation.MANDATORY}를 충족시킨다({@code StockServiceTest}와 같은 관례).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderConfirmServiceTest {

    private static final Long 회원 = 1L;
    private static final Long 다른회원 = 999L;

    @Autowired
    OrderConfirmPort orderConfirmPort;
    @Autowired
    OrderRepository orderRepository;

    @Test
    @DisplayName("남의 주문은 존재를 숨긴다 — ORDER_NOT_FOUND(404)")
    void 타인_주문_404() {
        Order order = 결제대기_주문_저장();

        assertThatThrownBy(() -> orderConfirmPort.lockPendingOrder(order.getOrderNo(), 다른회원))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 주문번호도 ORDER_NOT_FOUND")
    void 없는_주문_404() {
        assertThatThrownBy(() -> orderConfirmPort.lockPendingOrder("ORD-NONE", 회원))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("PENDING이 아니면 PAYMENT_ALREADY_CONFIRMED")
    void 이미_결제됨() {
        Order order = 결제대기_주문_저장();
        orderConfirmPort.markPaid(order.getId(), LocalDateTime.now());

        assertThatThrownBy(() -> orderConfirmPort.lockPendingOrder(order.getOrderNo(), 회원))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_ALREADY_CONFIRMED);
    }

    @Test
    @DisplayName("markPaid는 PENDING→PAID로 전이하고 전이된 상태를 돌려준다")
    void 결제완료_전이() {
        Order order = 결제대기_주문_저장();

        String status = orderConfirmPort.markPaid(order.getId(), LocalDateTime.now());

        assertThat(status).isEqualTo(Order.STATUS_PAID);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(Order.STATUS_PAID);
    }

    @Test
    @DisplayName("stockLines는 optionId 있는 줄만 담는다 — 옵션 없는 상품은 재고 비관리")
    void 재고줄_필터링() {
        Order order = 결제대기_주문_저장();

        OrderConfirmPort.ConfirmTarget target = orderConfirmPort.lockPendingOrder(order.getOrderNo(), 회원);

        assertThat(target.stockLines()).hasSize(1);
        assertThat(target.stockLines().get(0).optionId()).isNotNull();
        assertThat(target.stockLines().get(0).quantity()).isEqualTo(2);
        assertThat(target.orderId()).isEqualTo(order.getId());
        assertThat(target.orderNo()).isEqualTo(order.getOrderNo());
        assertThat(target.payableAmount()).isEqualTo(order.getPayableAmount());
    }

    @Test
    @DisplayName("트랜잭션 밖 호출은 예외 — MANDATORY 계약")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 트랜잭션_강제() {
        // 락으로 동시 승인을 직렬화한다는 계약은 호출자의 트랜잭션 안에서만 성립한다.
        assertThatThrownBy(() -> orderConfirmPort.lockPendingOrder("ORD-ANY", 회원))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    /** 옵션 있는 줄(수량 2) + 옵션 없는 줄로 구성한다 — stockLines 필터링을 볼 수 있게. */
    private Order 결제대기_주문_저장() {
        Order order = new Order("ORD-" + System.nanoTime(), 회원, "홍길동", "010-0000-0000",
                "06234", "서울시 강남구", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(1L, 10L, "토너", "100ml", 8000, 2));
        order.addItem(new OrderItem(2L, null, "사은품 파우치", null, 0, 1));
        return orderRepository.saveAndFlush(order);
    }
}

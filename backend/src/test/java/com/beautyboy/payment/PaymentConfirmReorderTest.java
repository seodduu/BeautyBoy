package com.beautyboy.payment;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsOption;
import com.beautyboy.catalog.GoodsOptionRepository;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.order.Order;
import com.beautyboy.order.OrderItem;
import com.beautyboy.order.OrderRepository;
import com.beautyboy.payment.dto.PaymentApproval;
import com.beautyboy.payment.dto.PaymentConfirmRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 차감 순서 변경(설계 §2)의 계약 검증.
 *
 * <p>지키려는 성질 한 줄: <b>재고 차감은 토스 승인 뒤에 일어나고, 승인 후 품절은 기존
 * 보상 경로(승인을_되돌린다)가 그대로 잡는다.</b> 신규 실패 경로는 없다 —
 * 순서만 바뀌었지 실패를 다루는 코드는 하나도 늘지 않았다는 것이 이 클래스의 주장이다.
 *
 * <p>클래스 {@code @Transactional}이 없는 이유는 {@link PaymentCompensationTest}와 같다 —
 * 보상 행의 REQUIRES_NEW 생존과 롤백 경계가 검증 대상이라 테스트가 트랜잭션을 감싸면 안 된다.
 * 같은 이유로 전용 H2 데이터베이스를 쓴다(공용 인메모리 DB를 커밋으로 오염시키지 않는다).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:confirmreorder;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
class PaymentConfirmReorderTest {

    private static final Long 회원 = 4101L;
    private static final int 단가 = 16_000;

    /** 직전 픽스처가 만든 옵션 id. 주문 항목을 지연 로딩하지 않고 재고를 재조회하려고 들고 있는다. */
    private Long 만든_옵션;

    @TestConfiguration
    static class 가짜_게이트웨이_설정 {
        @Bean
        @Primary
        승인기록_게이트웨이 fakeGateway() {
            return new 승인기록_게이트웨이();
        }
    }

    /** 승인액 조작 + confirm 호출 횟수 기록. 취소 기록·예외 주입은 FakeCancelGateway 그대로. */
    static class 승인기록_게이트웨이 extends FakeCancelGateway {
        int approvedAmount;
        final AtomicInteger confirm호출 = new AtomicInteger();

        @Override
        public PaymentApproval confirm(String paymentKey, String orderNo, int amount) {
            confirm호출.incrementAndGet();
            return new PaymentApproval(paymentKey, approvedAmount, "DONE", "{\"raw\":true}");
        }
    }

    @Autowired
    PaymentService paymentService;
    @Autowired
    승인기록_게이트웨이 gateway;
    @Autowired
    PaymentCompensationRepository compensationRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    GoodsOptionRepository goodsOptionRepository;
    @Autowired
    BrandRepository brandRepository;

    @BeforeEach
    void 초기화() {
        gateway.reset();
        gateway.confirm호출.set(0);
        gateway.approvedAmount = 단가;
        compensationRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("승인 후 품절이면 전액 취소되고 주문은 PENDING으로 남는다")
    void 승인후_품절이면_전액취소되고_주문은_PENDING으로_남는다() {
        Order 주문 = 결제대기_주문_저장(0);        // 재고 0 — 차감이 반드시 실패한다

        assertThatThrownBy(() -> paymentService.confirm(회원, 승인요청(주문)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_OUT_OF_STOCK);

        assertThat(gateway.confirm호출.get()).as("돈이 먼저 움직였다 — 그래서 되돌려야 한다").isEqualTo(1);
        assertThat(gateway.recorded()).hasSize(1);
        assertThat(gateway.recorded().get(0).amount()).as("전액 취소").isNull();
        assertThat(compensationRepository.findAll()).as("취소가 성공했으니 흔적 불필요").isEmpty();
        assertThat(주문상태(주문)).isEqualTo(Order.STATUS_PENDING);
        assertThat(재고()).isZero();
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("승인 후 품절이고 취소도 실패하면 PENDING_RETRY 보상 행이 남는다")
    void 승인후_품절이고_취소도_실패하면_PENDING_RETRY_보상행이_남는다() {
        Order 주문 = 결제대기_주문_저장(0);
        gateway.failNext(new PaymentGatewayException("토스 취소 응답 없음", null, false, null));

        assertThatThrownBy(() -> paymentService.confirm(회원, 승인요청(주문)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .as("원래 실패 원인이 그대로 올라와야 한다 — 보상 실패가 원인을 덮으면 안 된다")
                .isEqualTo(ErrorCode.ORDER_OUT_OF_STOCK);

        List<PaymentCompensation> 행들 = compensationRepository.findAll();
        assertThat(행들).hasSize(1);
        PaymentCompensation 행 = 행들.get(0);
        assertThat(행.getStatus()).isEqualTo(PaymentCompensation.STATUS_PENDING_RETRY);
        assertThat(행.getAction()).isEqualTo(PaymentCompensation.ACTION_CANCEL_FULL);
        assertThat(행.getOrderNo()).isEqualTo(주문.getOrderNo());
        assertThat(행.getAmount()).as("결제액 전액이 미취소로 남았다").isEqualTo(단가);
        assertThat(주문상태(주문)).isEqualTo(Order.STATUS_PENDING);
    }

    @Test
    @DisplayName("승인 전 품절 검증은 더 이상 없다 — 재고 0이어도 토스는 호출된다")
    void 승인전_품절검증은_더이상_없다() {
        Order 주문 = 결제대기_주문_저장(0);

        assertThatThrownBy(() -> paymentService.confirm(회원, 승인요청(주문)))
                .isInstanceOf(BusinessException.class);

        // 순서 변경의 트레이드오프를 못 박는다(설계 §2) — 품절도 토스를 한 번 부른다.
        assertThat(gateway.confirm호출.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("금액 불일치는 기존과 동일하게 동작한다 — 즉시 취소 + 409")
    void 금액불일치는_기존과_동일하게_동작한다() {
        Order 주문 = 결제대기_주문_저장(10);
        gateway.approvedAmount = 10;              // 토스가 알려온 승인액(조작됨)

        assertThatThrownBy(() -> paymentService.confirm(회원, 승인요청(주문)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        assertThat(gateway.recorded()).hasSize(1);
        assertThat(gateway.recorded().get(0).amount()).isNull();
        // 금액 대조가 차감보다 앞이므로 재고는 손대지 않은 채다.
        assertThat(재고()).isEqualTo(10);
        assertThat(주문상태(주문)).isEqualTo(Order.STATUS_PENDING);
    }

    @Test
    @DisplayName("정상 경로는 승인 후 차감으로 재고가 줄고 주문이 PAID가 된다")
    void 정상경로는_승인후_차감으로_확정된다() {
        Order 주문 = 결제대기_주문_저장(10);

        paymentService.confirm(회원, 승인요청(주문));

        assertThat(gateway.recorded()).isEmpty();
        assertThat(재고()).isEqualTo(9);
        assertThat(주문상태(주문)).isEqualTo(Order.STATUS_PAID);
    }

    private PaymentConfirmRequest 승인요청(Order 주문) {
        return new PaymentConfirmRequest(주문.getOrderNo(), "pk_" + 주문.getOrderNo(), 단가);
    }

    private String 주문상태(Order 주문) {
        return orderRepository.findByOrderNo(주문.getOrderNo()).orElseThrow().getStatus();
    }

    /** 픽스처가 만든 옵션을 DB에서 다시 읽는다 — 주문에서 타고 들어가면 지연 로딩이 터진다. */
    private int 재고() {
        return goodsOptionRepository.findById(만든_옵션).orElseThrow().getStock();
    }

    /** 옵션 있는 줄 하나(단가 = payableAmount, 수량 1). 재고만 케이스별로 다르다. */
    private Order 결제대기_주문_저장(int 재고) {
        Brand brand = brandRepository.saveAndFlush(new Brand("브랜드" + System.nanoTime(), null));
        Goods goods = new Goods(brand, "C001001001", "토너", null, "https://img/x.jpg", 30_000, 단가);
        goods.getOptions().add(new GoodsOption(goods, "200ml", 0, 재고, 0));
        GoodsOption option = goodsRepository.saveAndFlush(goods).getOptions().get(0);
        만든_옵션 = option.getId();

        Order order = new Order("ORD-" + System.nanoTime(), 회원, "홍길동", "010-0000-0000",
                "06234", "서울시 강남구", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(goods.getId(), option.getId(), "토너", "200ml", 단가, 1));
        return orderRepository.saveAndFlush(order);
    }
}

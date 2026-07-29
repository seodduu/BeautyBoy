package com.beautyboy.payment;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsOption;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.order.Order;
import com.beautyboy.order.OrderItem;
import com.beautyboy.order.OrderRepository;
import com.beautyboy.order.PostOrderTasks;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * 승인 후 로컬 실패 보상(설계 §5-1)의 계약 검증.
 *
 * <p>지키려는 성질 한 줄: <b>토스가 승인한 뒤 우리 쪽이 실패하면, 즉시 취소를 시도하고
 * 그 취소마저 실패하면 반드시 흔적(PENDING_RETRY 행)을 남긴다.</b> 흔적이 없으면 고객 돈이
 * 빠져나간 채로 아무도 모른다.
 *
 * <p>클래스 {@code @Transactional}이 없는 이유는 {@code PaymentCancelServiceTest}와 같다 —
 * 보상 행의 REQUIRES_NEW 생존이 검증 대상이다.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentCompensationTest {

    private static final Long 회원 = 1L;
    private static final int 주문금액 = 16_000;

    @TestConfiguration
    static class 가짜_게이트웨이_설정 {
        @Bean
        @Primary
        조작가능_게이트웨이 fakeGateway() {
            return new 조작가능_게이트웨이();
        }
    }

    /** 승인액을 테스트마다 조작할 수 있는 게이트웨이. 취소 기록·예외 주입은 FakeCancelGateway와 같다. */
    static class 조작가능_게이트웨이 extends FakeCancelGateway {
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
    조작가능_게이트웨이 gateway;
    @Autowired
    PaymentCompensationRepository compensationRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    BrandRepository brandRepository;

    /**
     * 승인 이후 단계를 실패시키는 지렛대. {@code @MockitoSpyBean}으로 {@code OrderConfirmPort}를
     * 감싸는 방법은 쓰지 않는다 — 그 빈은 CGLIB 트랜잭션 프록시라 {@code when(spy).method()}가
     * 실제 메서드를 호출해 버리고, MANDATORY 계약에 걸려 엉뚱한 예외가 난다.
     */
    @MockitoBean
    PostOrderTasks postOrderTasks;

    @BeforeEach
    void 초기화() {
        gateway.reset();
        gateway.confirm호출.set(0);
        gateway.approvedAmount = 주문금액;
        compensationRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("금액 불일치 — 즉시 취소가 성공하면 보상 행이 없다")
    void 금액불일치_즉시취소_성공이면_보상행이_없다() {
        Order 주문 = 결제대기_주문_저장();
        gateway.approvedAmount = 10;        // 토스가 알려온 승인액(조작됨)

        assertThatThrownBy(() -> paymentService.confirm(회원, 승인요청(주문, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        // 전액 취소가 성공했으므로(amount == null) 남길 흔적이 없다.
        assertThat(gateway.recorded()).hasSize(1);
        assertThat(gateway.recorded().get(0).amount()).isNull();
        assertThat(compensationRepository.findAll()).isEmpty();
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("금액 불일치 — 취소도 실패하면 PENDING_RETRY 행이 남는다")
    void 금액불일치_취소도_실패하면_PENDING_RETRY행이_남는다() {
        Order 주문 = 결제대기_주문_저장();
        gateway.approvedAmount = 10;
        gateway.failNext(new PaymentGatewayException("토스 취소 응답 없음", null, false, null));

        assertThatThrownBy(() -> paymentService.confirm(회원, 승인요청(주문, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .as("원래 실패 원인이 그대로 올라와야 한다 — 보상 실패가 원인을 덮으면 안 된다")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        List<PaymentCompensation> 행들 = compensationRepository.findAll();
        assertThat(행들).hasSize(1);
        PaymentCompensation 행 = 행들.get(0);
        assertThat(행.getStatus()).isEqualTo(PaymentCompensation.STATUS_PENDING_RETRY);
        assertThat(행.getAction()).isEqualTo(PaymentCompensation.ACTION_CANCEL_FULL);
        assertThat(행.getOrderNo()).isEqualTo(주문.getOrderNo());
        assertThat(행.getAmount()).isEqualTo(주문금액);
        assertThat(행.getRetryCount()).isZero();
        assertThat(행.getLastError()).isNotBlank();

        // 주문은 결제대기로 되돌아갔다 — 보상 행만 REQUIRES_NEW로 살아남았다.
        assertThat(orderRepository.findByOrderNo(주문.getOrderNo()).orElseThrow().getStatus())
                .isEqualTo(Order.STATUS_PENDING);
    }

    @Test
    @DisplayName("금액 불일치가 아닌 승인 후 실패도 즉시 취소를 시도한다 — 후처리 발행 실패")
    void 승인후_로컬실패면_즉시취소가_시도된다() {
        Order 주문 = 결제대기_주문_저장();
        doThrow(new IllegalStateException("아웃박스 발행 실패"))
                .when(postOrderTasks).onOrderConfirmed(any());

        assertThatThrownBy(() -> paymentService.confirm(회원, 승인요청(주문, 주문금액)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(gateway.confirm호출.get()).isEqualTo(1);          // 돈은 이미 움직였다
        assertThat(gateway.recorded()).hasSize(1);                   // 그래서 되돌려야 한다
        assertThat(gateway.recorded().get(0).amount()).isNull();     // 전액 취소
        assertThat(compensationRepository.findAll()).isEmpty();      // 취소가 성공했으니 흔적 불필요
    }

    @Test
    @DisplayName("정상 승인은 보상 경로를 타지 않는다 — 취소 호출도 보상 행도 없다")
    void 정상승인은_보상경로를_타지_않는다() {
        Order 주문 = 결제대기_주문_저장();

        paymentService.confirm(회원, 승인요청(주문, 주문금액));

        assertThat(gateway.recorded()).isEmpty();
        assertThat(compensationRepository.findAll()).isEmpty();
        assertThat(orderRepository.findByOrderNo(주문.getOrderNo()).orElseThrow().getStatus())
                .isEqualTo(Order.STATUS_PAID);
    }

    private PaymentConfirmRequest 승인요청(Order 주문, int amount) {
        return new PaymentConfirmRequest(주문.getOrderNo(), "pk_" + 주문.getOrderNo(), amount);
    }

    /** 옵션 있는 줄 하나(단가 = payableAmount, 수량 1). 재고는 충분히 둔다 — 여기 관심사가 아니다. */
    private Order 결제대기_주문_저장() {
        Brand brand = brandRepository.saveAndFlush(new Brand("브랜드" + System.nanoTime(), null));
        Goods goods = new Goods(brand, "C001001001", "토너", null, "https://img/x.jpg",
                30_000, 주문금액);
        goods.getOptions().add(new GoodsOption(goods, "200ml", 0, 100, 0));
        GoodsOption option = goodsRepository.saveAndFlush(goods).getOptions().get(0);

        Order order = new Order("ORD-" + System.nanoTime(), 회원, "홍길동", "010-0000-0000",
                "06234", "서울시 강남구", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(goods.getId(), option.getId(), "토너", "200ml", 주문금액, 1));
        return orderRepository.saveAndFlush(order);
    }
}

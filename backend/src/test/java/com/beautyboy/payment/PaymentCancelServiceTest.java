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
import com.beautyboy.order.OrderCancelRepository;
import com.beautyboy.order.OrderItem;
import com.beautyboy.order.OrderRepository;
import com.beautyboy.order.dto.OrderCancelRequest;
import com.beautyboy.order.dto.OrderCancelResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 취소 오케스트레이션의 순서 계약 검증(설계 §4).
 *
 * <p><b>클래스에 {@code @Transactional}을 걸지 않는다.</b> 이 테스트가 보려는 것이 정확히
 * "메인 트랜잭션이 롤백돼도 REQUIRES_NEW 보상 행은 남는가"이고, 테스트가 바깥 트랜잭션을
 * 열어버리면 서비스의 {@code @Transactional}이 거기 참여해 롤백 경계가 사라진다.
 * 대신 각 테스트가 {@link #픽스처를_지운다()}로 스스로 정리한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentCancelServiceTest {

    private static final Long 회원 = 1L;
    private static final Long 다른회원 = 999L;
    private static final int 토너_단가 = 24_100;
    private static final int 초기_재고 = 50;

    /** 취소 호출을 기록하고, 주입된 예외를 던진다. e2e 프로필 Fake와 같은 역할의 test 프로필 판. */
    @TestConfiguration
    static class 가짜_게이트웨이_설정 {
        @Bean
        @Primary
        FakeCancelGateway fakeCancelGateway() {
            return new FakeCancelGateway();
        }
    }

    @Autowired
    PaymentCancelService paymentCancelService;
    @Autowired
    FakeCancelGateway gateway;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderCancelRepository orderCancelRepository;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    PaymentCompensationRepository compensationRepository;
    @Autowired
    GoodsOptionRepository goodsOptionRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    void 초기화() {
        gateway.reset();
        픽스처를_지운다();
    }

    @Test
    @DisplayName("취소 성공 — 토스에 스냅샷 환불액이 그대로 전달된다")
    void 취소성공_토스에_스냅샷환불액이_전달된다() {
        주문_상황 상황 = 결제완료_주문을_만든다();

        OrderCancelResponse response = paymentCancelService.cancel(
                회원, 상황.orderNo(), 취소요청(상황.orderItemId(), 1));

        assertThat(response.orderNo()).isEqualTo(상황.orderNo());
        assertThat(response.status()).isEqualTo(Order.STATUS_PARTIALLY_CANCELED);
        assertThat(response.refundAmount()).isEqualTo(토너_단가);
        assertThat(response.canceledAt()).isNotNull();

        assertThat(gateway.recorded()).hasSize(1);
        assertThat(gateway.recorded().get(0).amount()).isEqualTo(토너_단가);
        assertThat(gateway.recorded().get(0).paymentKey()).isEqualTo(상황.paymentKey());

        // 재고가 취소 수량만큼 돌아왔다.
        assertThat(재고(상황.optionId())).isEqualTo(초기_재고 - 2 + 1);
    }

    @Test
    @DisplayName("취소 성공 — 보상 행이 커밋 후 DONE이 된다")
    void 취소성공_보상행이_DONE이_된다() {
        주문_상황 상황 = 결제완료_주문을_만든다();

        paymentCancelService.cancel(회원, 상황.orderNo(), 취소요청(상황.orderItemId(), 1));

        // 서비스 트랜잭션은 이미 커밋됐다(테스트가 트랜잭션을 열지 않는다) — afterCommit 콜백이 돌았다.
        List<PaymentCompensation> 행들 = compensationRepository.findAll();
        assertThat(행들).hasSize(1);
        assertThat(행들.get(0).getStatus()).isEqualTo(PaymentCompensation.STATUS_DONE);
        assertThat(행들.get(0).getAction()).isEqualTo(PaymentCompensation.ACTION_CANCEL_PARTIAL);
        assertThat(행들.get(0).getAmount()).isEqualTo(토너_단가);
        assertThat(행들.get(0).getPaymentKey()).isEqualTo(상황.paymentKey());
    }

    @Test
    @DisplayName("토스가 응답 있는 실패면 전부 롤백되고 보상 행은 VOID다")
    void 토스_응답있는_실패면_전부_롤백되고_보상행은_VOID다() {
        주문_상황 상황 = 결제완료_주문을_만든다();
        gateway.failNext(new PaymentGatewayException("400 BAD_REQUEST", null, true, "INVALID_REQUEST"));

        assertThatThrownBy(() -> paymentCancelService.cancel(
                회원, 상황.orderNo(), 취소요청(상황.orderItemId(), 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_CANCEL_FAILED);

        // 로컬 반영은 전부 되돌아갔다 — 롤백이 곧 복원이다(설계 §4).
        assertThat(새_트랜잭션에서_읽는다(() ->
                orderRepository.findByOrderNo(상황.orderNo()).orElseThrow().getStatus()))
                .isEqualTo(Order.STATUS_PAID);
        assertThat(새_트랜잭션에서_읽는다(() -> orderRepository.findByOrderNo(상황.orderNo())
                .orElseThrow().getItems().get(0).getCanceledQuantity())).isZero();
        assertThat(orderCancelRepository.findByOrderIdOrderByIdAsc(상황.orderId())).isEmpty();
        assertThat(재고(상황.optionId())).isEqualTo(초기_재고 - 2);   // 복원이 되돌아갔다

        // 보상 행만은 살아남는다 — REQUIRES_NEW라서. 이 행이 이 기능의 존재 이유다(설계 §5-1).
        List<PaymentCompensation> 행들 = compensationRepository.findAll();
        assertThat(행들).hasSize(1);
        assertThat(행들.get(0).getStatus()).isEqualTo(PaymentCompensation.STATUS_VOID);
        assertThat(행들.get(0).getLastError()).isNotBlank();
    }

    @Test
    @DisplayName("토스가 응답 없는 실패면 보상 행은 UNVERIFIED다 — 환불됐는지 모른다")
    void 토스_응답없는_실패면_보상행은_UNVERIFIED다() {
        주문_상황 상황 = 결제완료_주문을_만든다();
        gateway.failNext(new PaymentGatewayException("타임아웃", null, false, null));

        assertThatThrownBy(() -> paymentCancelService.cancel(
                회원, 상황.orderNo(), 취소요청(상황.orderItemId(), 1)))
                .isInstanceOf(BusinessException.class);

        List<PaymentCompensation> 행들 = compensationRepository.findAll();
        assertThat(행들).hasSize(1);
        assertThat(행들.get(0).getStatus()).isEqualTo(PaymentCompensation.STATUS_UNVERIFIED);
    }

    @Test
    @DisplayName("PAID인데 결제가 없으면 IllegalStateException — 정합 버그 감지")
    void PAID인데_결제가_없으면_IllegalStateException() {
        주문_상황 상황 = 결제완료_주문을_만든다();
        paymentRepository.deleteAll();      // 있을 수 없는 상태를 일부러 만든다

        assertThatThrownBy(() -> paymentCancelService.cancel(
                회원, 상황.orderNo(), 취소요청(상황.orderItemId(), 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(상황.orderNo());
    }

    @Test
    @DisplayName("검증 실패는 토스를 부르지도 않는다 — 남의 주문")
    void 남의_주문이면_토스를_부르지_않는다() {
        주문_상황 상황 = 결제완료_주문을_만든다();

        assertThatThrownBy(() -> paymentCancelService.cancel(
                다른회원, 상황.orderNo(), 취소요청(상황.orderItemId(), 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);

        assertThat(gateway.recorded()).isEmpty();
        assertThat(compensationRepository.findAll()).isEmpty();   // 의도 행도 만들지 않는다
    }

    private OrderCancelRequest 취소요청(Long orderItemId, int quantity) {
        return new OrderCancelRequest(
                List.of(new OrderCancelRequest.Item(orderItemId, quantity)), "단순 변심");
    }

    /**
     * 테스트가 트랜잭션을 열지 않으므로 서비스는 이미 커밋됐고, 조회는 새 트랜잭션에서 한다.
     * 지연 로딩 컬렉션(order.items)을 만지려면 세션이 열려 있어야 해서 읽기도 트랜잭션 안이다.
     */
    private <T> T 새_트랜잭션에서_읽는다(java.util.function.Supplier<T> 조회) {
        return transactionTemplate.execute(status -> 조회.get());
    }

    private int 재고(Long optionId) {
        return 새_트랜잭션에서_읽는다(
                () -> goodsOptionRepository.findById(optionId).orElseThrow().getStock());
    }

    private record 주문_상황(Long orderId, String orderNo, Long orderItemId, Long optionId,
                         String paymentKey) {
    }

    /**
     * 옵션 있는 줄(토너 수량 2)만 담은 결제완료 주문 + 그 결제 행. 재고는 이미 2 깎인 상태로
     * 둔다 — 승인 경로를 다시 태우지 않고 취소의 사후 상태만 재현하기 위해서다.
     */
    private 주문_상황 결제완료_주문을_만든다() {
        GoodsOption option = 옵션_저장(초기_재고 - 2);

        Order order = new Order("ORD-" + System.nanoTime(), 회원, "홍길동", "010-0000-0000",
                "06234", "서울시 강남구", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(1L, option.getId(), "토너", "200ml", 토너_단가, 2));
        order.markPaid(LocalDateTime.now());
        Order saved = orderRepository.saveAndFlush(order);

        String paymentKey = "pk_" + System.nanoTime();
        paymentRepository.saveAndFlush(new Payment(saved.getId(), paymentKey,
                saved.getPayableAmount(), "{\"raw\":true}", LocalDateTime.now()));

        return new 주문_상황(saved.getId(), saved.getOrderNo(),
                saved.getItems().get(0).getId(), option.getId(), paymentKey);
    }

    private void 픽스처를_지운다() {
        compensationRepository.deleteAll();
        paymentRepository.deleteAll();
        orderCancelRepository.deleteAll();
        orderRepository.deleteAll();
    }

    /** 브랜드·상품·옵션을 한 번에 커밋한다(PaymentStockConfirmTest와 같은 관례). */
    private GoodsOption 옵션_저장(int stock) {
        Brand brand = brandRepository.saveAndFlush(new Brand("브랜드" + System.nanoTime(), null));
        Goods goods = new Goods(brand, "C001001001", "토너", null, "https://img/x.jpg",
                30_000, 토너_단가);
        goods.getOptions().add(new GoodsOption(goods, "200ml", 0, stock, 0));
        return goodsRepository.saveAndFlush(goods).getOptions().get(0);
    }
}

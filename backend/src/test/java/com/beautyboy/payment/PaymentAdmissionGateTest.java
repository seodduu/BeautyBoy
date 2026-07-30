package com.beautyboy.payment;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsOption;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.catalog.StockAdmission;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * confirm의 선점 게이트 계약(설계 §5의 (2)(3)(4)줄).
 *
 * <p>지키려는 성질 두 줄: <b>거절된 요청은 토스를 부르지 않는다</b>, 그리고
 * <b>선점한 입장권은 실패 경로에서 정확히 한 번 반환된다</b>. 앞의 것이 무너지면 2단계가
 * 존재할 이유가 없고, 뒤의 것이 무너지면 카운터가 실제보다 작아져 팔 수 있는 재고를 거절한다.
 *
 * <p>{@link StockAdmission}은 여기서 <b>가짜</b>다 — 실 Redis의 Lua 판정은
 * {@code RedisStockAdmissionIT}가, 둘을 합친 실제 폭주는 {@code HotSkuConcurrencyIT}가 본다.
 * 이 클래스가 보는 것은 confirm이 그 계약을 어떻게 호출하는지 하나뿐이다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:admissiongate;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
class PaymentAdmissionGateTest {

    private static final Long 회원 = 4201L;
    private static final int 단가 = 16_000;

    @TestConfiguration
    static class 가짜_배선 {
        @Bean
        @Primary
        승인기록_게이트웨이 fakeGateway() {
            return new 승인기록_게이트웨이();
        }

        @Bean
        @Primary
        가짜_선점 fakeAdmission() {
            return new 가짜_선점();
        }
    }

    static class 승인기록_게이트웨이 extends FakeCancelGateway {
        int approvedAmount;
        boolean confirm이_터진다;
        final AtomicInteger confirm호출 = new AtomicInteger();

        @Override
        public PaymentApproval confirm(String paymentKey, String orderNo, int amount) {
            confirm호출.incrementAndGet();
            if (confirm이_터진다) {
                throw new PaymentGatewayException("토스 통신 실패(주입)", null);
            }
            return new PaymentApproval(paymentKey, approvedAmount, "DONE", "{\"raw\":true}");
        }
    }

    /** 통과/거절을 테스트가 정하고, release 호출을 세는 더블. */
    static class 가짜_선점 implements StockAdmission {
        boolean 통과 = true;
        final AtomicInteger tryAcquire호출 = new AtomicInteger();
        final List<List<Line>> release호출 = new ArrayList<>();

        @Override
        public boolean tryAcquire(List<Line> lines) {
            tryAcquire호출.incrementAndGet();
            return 통과;
        }

        @Override
        public void release(List<Line> lines) {
            release호출.add(List.copyOf(lines));
        }

        void 초기화() {
            통과 = true;
            tryAcquire호출.set(0);
            release호출.clear();
        }
    }

    @Autowired
    PaymentService paymentService;
    @Autowired
    승인기록_게이트웨이 gateway;
    @Autowired
    가짜_선점 admission;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    PaymentCompensationRepository compensationRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    BrandRepository brandRepository;

    @BeforeEach
    void 초기화() {
        gateway.reset();
        gateway.confirm호출.set(0);
        gateway.confirm이_터진다 = false;
        gateway.approvedAmount = 단가;
        admission.초기화();
        compensationRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("선점이 거절하면 토스를 호출하지 않는다 — 2단계의 존재 이유")
    void 선점_거절이면_토스를_호출하지_않는다() {
        admission.통과 = false;
        Order 주문 = 결제대기_주문_저장(10);

        assertThatThrownBy(() -> paymentService.confirm(회원, 승인요청(주문)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .as("선점 거절도 신규 ErrorCode 없이 기존 품절 코드다")
                .isEqualTo(ErrorCode.ORDER_OUT_OF_STOCK);

        assertThat(gateway.confirm호출.get()).as("돈이 움직이기 전에 끝났다").isZero();
        assertThat(admission.release호출)
                .as("선점하지 못했으므로 반환할 것도 없다").isEmpty();
    }

    @Test
    @DisplayName("토스 승인이 실패하면 선점이 반환된다 — 돈도 재고도 안 움직였다")
    void 토스_승인실패면_선점이_반환된다() {
        gateway.confirm이_터진다 = true;
        Order 주문 = 결제대기_주문_저장(10);

        assertThatThrownBy(() -> paymentService.confirm(회원, 승인요청(주문)))
                .isInstanceOf(PaymentGatewayException.class);

        assertThat(admission.release호출).hasSize(1);
        assertThat(gateway.recorded()).as("승인이 안 됐으니 취소할 것도 없다").isEmpty();
    }

    @Test
    @DisplayName("승인 후 실패면 선점 반환과 승인 취소가 모두 일어난다")
    void 승인후_실패면_선점반환과_승인취소가_모두_일어난다() {
        Order 주문 = 결제대기_주문_저장(0);        // 재고 0 — 승인 뒤 차감이 실패한다

        assertThatThrownBy(() -> paymentService.confirm(회원, 승인요청(주문)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_OUT_OF_STOCK);

        assertThat(admission.release호출).hasSize(1);
        assertThat(gateway.recorded()).hasSize(1);
        assertThat(gateway.recorded().get(0).amount()).as("전액 취소").isNull();
    }

    @Test
    @DisplayName("선점을 통과한 정상 경로는 release가 없다 — 입장권은 팔린 채로 남는다")
    void 선점_통과후_정상경로는_release가_없다() {
        Order 주문 = 결제대기_주문_저장(10);

        paymentService.confirm(회원, 승인요청(주문));

        assertThat(admission.tryAcquire호출.get()).isEqualTo(1);
        assertThat(admission.release호출)
                .as("여기서 반환하면 카운터가 실제보다 커져 초과 입장을 허용한다").isEmpty();
    }

    private PaymentConfirmRequest 승인요청(Order 주문) {
        return new PaymentConfirmRequest(주문.getOrderNo(), "pk_" + 주문.getOrderNo(), 단가);
    }

    private Order 결제대기_주문_저장(int 재고) {
        Brand brand = brandRepository.saveAndFlush(new Brand("브랜드" + System.nanoTime(), null));
        Goods goods = new Goods(brand, "C001001001", "토너", null, "https://img/x.jpg", 30_000, 단가);
        goods.getOptions().add(new GoodsOption(goods, "200ml", 0, 재고, 0));
        GoodsOption option = goodsRepository.saveAndFlush(goods).getOptions().get(0);

        Order order = new Order("ORD-" + System.nanoTime(), 회원, "홍길동", "010-0000-0000",
                "06234", "서울시 강남구", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(goods.getId(), option.getId(), "토너", "200ml", 단가, 1));
        return orderRepository.saveAndFlush(order);
    }
}

package com.beautyboy.payment;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsOption;
import com.beautyboy.catalog.GoodsOptionRepository;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.common.BusinessException;
import com.beautyboy.member.MemberService;
import com.beautyboy.member.dto.SignupRequest;
import com.beautyboy.order.Order;
import com.beautyboy.order.OrderCancelRepository;
import com.beautyboy.order.OrderItem;
import com.beautyboy.order.OrderRepository;
import com.beautyboy.order.dto.OrderCancelRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 취소·보상의 <b>H2가 가릴 수 있는</b> 두 성질을 실 MySQL에서 확인한다.
 *
 * <ol>
 *   <li><b>REQUIRES_NEW 생존</b> — 메인 트랜잭션이 롤백돼도 보상 행이 남는가. 이것이 무너지면
 *       미취소 승인이 흔적 없이 사라진다.</li>
 *   <li><b>동시 취소 직렬화</b> — 같은 항목의 마지막 1개를 두 요청이 동시에 취소하려 할 때
 *       정확히 한쪽만 성공하고 재고 복원이 정확히 1회인가. 이 성질은 InnoDB 행 잠금에
 *       달려 있어 H2 녹색이 근거가 되지 못한다({@code StockConcurrencyMysqlIntegrationTest}와 같은 이유).</li>
 * </ol>
 *
 * <p>Flyway를 실제로 적용하고 {@code ddl-auto=validate}로 띄우므로 V94·V95 DDL과 엔티티
 * 매핑의 불일치(TINYINT↔int 같은 유형)도 여기서 함께 잡힌다.
 *
 * <p>클래스에 {@code @Transactional}이 없는 이유: 롤백 경계와 스레드별 커밋이 검증 대상이다.
 * 스케줄러는 초기 지연을 크게 줘 테스트 중 틱하지 않게 밀어낸다.
 *
 * <p>실행: {@code ./gradlew integrationTest}
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = "beautyboy.compensation.initial-delay-ms=3600000")
class PaymentCompensationMysqlIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @DynamicPropertySource
    static void 실_MySQL로_바꿔_끼운다(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    private static final int 단가 = 24_100;

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
    MemberService memberService;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderCancelRepository orderCancelRepository;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    PaymentCompensationRepository compensationRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    GoodsOptionRepository goodsOptionRepository;
    @Autowired
    BrandRepository brandRepository;

    /** 실 MySQL에는 {@code orders.member_id → member.id} 외래키가 걸려 있어 회원을 진짜로 가입시킨다. */
    private Long 회원;

    private final List<Long> 만든_주문 = new ArrayList<>();
    private final List<Long> 만든_상품 = new ArrayList<>();
    private final List<Long> 만든_브랜드 = new ArrayList<>();

    @BeforeEach
    void 준비() {
        gateway.reset();
        compensationRepository.deleteAll();
        회원 = 가입시킨다();
    }

    @AfterEach
    void 뒷정리() {
        compensationRepository.deleteAll();
        paymentRepository.deleteAll(paymentRepository.findAll().stream()
                .filter(p -> 만든_주문.contains(p.getOrderId()))
                .toList());
        만든_주문.forEach(id ->
                orderCancelRepository.deleteAll(orderCancelRepository.findByOrderIdOrderByIdAsc(id)));
        만든_주문.forEach(orderRepository::deleteById);
        만든_상품.forEach(goodsRepository::deleteById);
        만든_브랜드.forEach(brandRepository::deleteById);
        만든_주문.clear();
        만든_상품.clear();
        만든_브랜드.clear();
    }

    @Test
    @DisplayName("메인 트랜잭션이 롤백돼도 보상 행은 남는다 — REQUIRES_NEW 생존을 실 MySQL에서 확인")
    void 메인_트랜잭션이_롤백돼도_보상행은_남는다() {
        상황 s = 결제완료_주문(2, 10);
        gateway.failNext(new PaymentGatewayException("400 BAD_REQUEST", null, true, "INVALID_REQUEST"));

        assertThat(취소_시도(s, 1)).isInstanceOf(BusinessException.class);

        // 로컬은 전부 되돌아갔다.
        assertThat(orderRepository.findById(s.orderId()).orElseThrow().getStatus())
                .isEqualTo(Order.STATUS_PAID);
        assertThat(orderCancelRepository.findByOrderIdOrderByIdAsc(s.orderId())).isEmpty();
        assertThat(재고(s.optionId())).isEqualTo(10);

        // 보상 행만 살아남았다. H2에서 통과해도 실 MySQL에서 깨질 수 있는 자리라 여기서 다시 본다.
        List<PaymentCompensation> 행들 = compensationRepository.findAll();
        assertThat(행들).hasSize(1);
        assertThat(행들.get(0).getStatus()).isEqualTo(PaymentCompensation.STATUS_VOID);
    }

    @Test
    @DisplayName("같은 항목을 두 요청이 동시에 취소하면 한쪽만 성공하고 재고 복원은 정확히 1회다")
    void 동시취소는_한쪽만_성공한다() throws Exception {
        // 수량 1짜리 항목 — 두 요청이 각각 1개를 취소하려 하므로 하나는 반드시 잔여 초과다.
        상황 s = 결제완료_주문(1, 10);

        List<Object> 결과 = 동시에_취소한다(s);

        List<Object> 성공 = 결과.stream().filter(r -> !(r instanceof Throwable)).toList();
        List<Throwable> 실패 = 결과.stream()
                .filter(Throwable.class::isInstance).map(Throwable.class::cast).toList();

        assertThat(성공).as("수량 1을 두 번 취소할 수는 없다. 실제: %s", 결과).hasSize(1);
        assertThat(실패).hasSize(1);
        assertThat(실패.get(0))
                .as("패배자는 미분류 예외(→500)가 아니라 BusinessException이어야 한다. 실제: %s", 실패.get(0))
                .isInstanceOf(BusinessException.class);

        // 재고는 정확히 +1. +2면 이중 복원이고, +0이면 성공한 쪽이 복원하지 않았다는 뜻이다.
        assertThat(재고(s.optionId())).isEqualTo(11);
        assertThat(orderRepository.findById(s.orderId()).orElseThrow().getStatus())
                .isEqualTo(Order.STATUS_CANCELED);
        assertThat(orderCancelRepository.findByOrderIdOrderByIdAsc(s.orderId())).hasSize(1);
        assertThat(gateway.recorded()).as("토스 부분 취소도 정확히 한 번만 나간다").hasSize(1);
    }

    private List<Object> 동시에_취소한다(상황 s) throws Exception {
        Callable<Object> 한_번 = () -> {
            try {
                return paymentCancelService.cancel(회원, s.orderNo(), 취소요청(s, 1));
            } catch (Throwable t) {
                return t;
            }
        };
        ExecutorService 풀 = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> 미래 = 풀.invokeAll(List.of(한_번, 한_번));
            List<Object> 결과 = new ArrayList<>();
            for (Future<Object> f : 미래) {
                결과.add(f.get(30, TimeUnit.SECONDS));
            }
            return 결과;
        } finally {
            풀.shutdownNow();
        }
    }

    private Throwable 취소_시도(상황 s, int quantity) {
        try {
            paymentCancelService.cancel(회원, s.orderNo(), 취소요청(s, quantity));
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private OrderCancelRequest 취소요청(상황 s, int quantity) {
        return new OrderCancelRequest(
                List.of(new OrderCancelRequest.Item(s.orderItemId(), quantity)), "단순 변심");
    }

    private int 재고(Long optionId) {
        return goodsOptionRepository.findById(optionId).orElseThrow().getStock();
    }

    private record 상황(Long orderId, String orderNo, Long orderItemId, Long optionId) {
    }

    private 상황 결제완료_주문(int quantity, int 남은재고) {
        Brand brand = brandRepository.saveAndFlush(new Brand("브랜드" + System.nanoTime(), null));
        만든_브랜드.add(brand.getId());
        Goods goods = new Goods(brand, "C001001001", "토너", null, "https://img/x.jpg", 30_000, 단가);
        goods.getOptions().add(new GoodsOption(goods, "200ml", 0, 남은재고, 0));
        Goods 저장된_상품 = goodsRepository.saveAndFlush(goods);
        만든_상품.add(저장된_상품.getId());
        GoodsOption option = 저장된_상품.getOptions().get(0);

        Order order = new Order("ORD-" + System.nanoTime(), 회원, "홍길동", "010-0000-0000",
                "06234", "서울시 강남구", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(저장된_상품.getId(), option.getId(), "토너", "200ml", 단가, quantity));
        order.markPaid(LocalDateTime.now());
        Order saved = orderRepository.saveAndFlush(order);
        만든_주문.add(saved.getId());

        paymentRepository.saveAndFlush(new Payment(saved.getId(), "pk_" + saved.getOrderNo(),
                saved.getPayableAmount(), "{\"raw\":true}", LocalDateTime.now()));

        return new 상황(saved.getId(), saved.getOrderNo(), saved.getItems().get(0).getId(),
                option.getId());
    }

    /** Flyway 시드 회원과 겹치지 않도록 이메일에 UUID를 넣는다. */
    private Long 가입시킨다() {
        String email = "cancel-it-" + UUID.randomUUID().toString().substring(0, 8) + "@b.com";
        return memberService.signup(
                new SignupRequest(email, "pw123456", "취소테스터", null, null, null)).id();
    }
}

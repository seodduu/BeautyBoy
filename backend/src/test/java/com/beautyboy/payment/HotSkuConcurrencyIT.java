package com.beautyboy.payment;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsOption;
import com.beautyboy.catalog.GoodsOptionRepository;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.member.MemberService;
import com.beautyboy.member.dto.SignupRequest;
import com.beautyboy.order.Order;
import com.beautyboy.order.OrderItem;
import com.beautyboy.order.OrderRepository;
import com.beautyboy.outbox.OutboxEventRepository;
import com.beautyboy.payment.dto.PaymentApproval;
import com.beautyboy.payment.dto.PaymentConfirmRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 단일 SKU 폭주의 실측 — 실 MySQL + 실 Redis, 실제 빈 조립.
 *
 * <p>이 클래스가 판정하는 것은 <b>두 가지</b>다.
 * <ol>
 *   <li><b>초과 판매 0</b> — 재고 10에 동시 50이 몰려도 성공은 정확히 10건이다. 이 성질은
 *       선점 필터와 무관하게 DB 조건부 UPDATE가 지킨다(그래서 토글을 꺼도 같은 단언이 선다).</li>
 *   <li><b>승인 후 취소 0</b> — 선점이 켜져 있으면 패자는 <b>토스 호출 전에</b> 거절된다.
 *       이것이 2단계의 존재 이유이고, 게이트웨이의 cancel 호출 수가 유일한 판정 기준이다
 *       (설계 §7). 승인(confirm) 호출 수는 참고로 로그에만 남긴다.</li>
 * </ol>
 *
 * <p>k6가 아니라 통합 테스트로 판정하는 이유: k6는 지연 분포를 재지, "승인 후 취소가 몇 건
 * 일어났는가"를 세지 못한다. 그 수는 게이트웨이 호출 기록에만 남는다.
 *
 * <p><b>판매 집계 단언을 무엇으로 하는가(계획서 이탈, 근거 명시):</b> 계획서는
 * {@code goods_daily_stat} 합을 보라고 했지만 그 테이블은 Kafka 컨슈머
 * ({@code SalesAggregationConsumer})가 채운다 — 여기서는 브로커를 띄우지 않으므로 영원히 0이다.
 * 대신 <b>아웃박스 행 수</b>를 센다. 아웃박스 INSERT는 confirm 트랜잭션 <b>안에</b> 있어
 * 커밋된 성공 건수와 정확히 일치하며, 집계는 그 행에서 파생될 뿐이다 — 같은 성질을
 * 브로커 없이 재는 자리다.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "beautyboy.stock.admission=true")
class HotSkuConcurrencyIT {

    private static final Logger log = LoggerFactory.getLogger(HotSkuConcurrencyIT.class);

    static final int 재고 = 10;
    static final int 동시_요청 = 50;
    static final int 단가 = 16_000;

    @DynamicPropertySource
    static void 실_인프라로_바꿔_끼운다(DynamicPropertyRegistry registry) {
        HotSku컨테이너.등록한다(registry);
    }

    /** 요청 금액을 그대로 승인한다. 취소 호출을 세는 것이 이 더블의 진짜 임무다. */
    @TestConfiguration
    static class 가짜_게이트웨이_설정 {
        @Bean
        @Primary
        호출세는_게이트웨이 fakeGateway() {
            return new 호출세는_게이트웨이();
        }
    }

    static class 호출세는_게이트웨이 extends FakeCancelGateway {
        final java.util.concurrent.atomic.AtomicInteger confirm호출 =
                new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public PaymentApproval confirm(String paymentKey, String orderNo, int amount) {
            confirm호출.incrementAndGet();
            return new PaymentApproval(paymentKey, amount, "DONE", "{\"raw\":true}");
        }
    }

    @Autowired
    PaymentService paymentService;
    @Autowired
    호출세는_게이트웨이 gateway;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    MemberService memberService;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OutboxEventRepository outboxEventRepository;
    @Autowired
    GoodsOptionRepository goodsOptionRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    BrandRepository brandRepository;

    private Long 회원;

    @BeforeEach
    void 초기화() {
        gateway.reset();
        gateway.confirm호출.set(0);
        // 콜드 시작을 명시한다 — 앞 테스트가 남긴 카운터를 물려받으면 선점 결과가 달라진다.
        redis.delete(redis.keys("stock:adm:*"));
        outboxEventRepository.deleteAll();
        회원 = 가입시킨다();
    }

    @Test
    @DisplayName("동시 confirm은 초과 판매를 만들지 않는다 — 재고 10에 50이 몰려도 성공 10")
    void 동시_confirm은_초과판매를_만들지_않는다() throws Exception {
        GoodsOption 옵션 = 옵션_저장(재고);
        List<Order> 주문들 = 주문_저장(옵션, 동시_요청);

        List<Object> 결과 = 동시에_승인한다(주문들);

        assertThat(성공_수(결과)).as("재고보다 많이 팔렸다면 최종 방어선이 무너진 것이다").isEqualTo(재고);
        assertThat(goodsOptionRepository.findById(옵션.getId()).orElseThrow().getStock())
                .as("음수면 이중 차감의 흔적이다").isZero();
        assertThat(outboxEventRepository.count())
                .as("커밋된 성공 건수와 확정 이벤트 수는 같아야 한다").isEqualTo(재고);
    }

    @Test
    @DisplayName("선점이 켜지면 패자는 승인 전에 거절된다 — 승인 후 취소 0건")
    void 패자는_승인전에_거절된다() throws Exception {
        GoodsOption 옵션 = 옵션_저장(재고);
        List<Order> 주문들 = 주문_저장(옵션, 동시_요청);

        List<Object> 결과 = 동시에_승인한다(주문들);

        // 판정 기준은 이 한 줄이다(설계 §7). 승인 호출 수는 참고로만 남긴다.
        log.info("[참고] 토스 승인 호출 {}회, 성공 {}건 (판정 대상 아님)",
                gateway.confirm호출.get(), 성공_수(결과));
        assertThat(gateway.recorded())
                .as("패자가 토스를 부른 뒤 취소됐다면 2단계가 제 일을 못한 것이다")
                .isEmpty();
        assertThat(성공_수(결과)).isEqualTo(재고);
    }

    private List<Object> 동시에_승인한다(List<Order> 주문들) throws Exception {
        return HotSku폭주.동시에_승인한다(paymentService, 회원, 주문들);
    }

    private long 성공_수(List<Object> 결과) {
        return 결과.stream().filter(r -> !(r instanceof Throwable)).count();
    }

    private Long 가입시킨다() {
        return HotSku폭주.가입시킨다(memberService);
    }

    private GoodsOption 옵션_저장(int 재고) {
        return HotSku폭주.옵션_저장(brandRepository, goodsRepository, 재고);
    }

    private List<Order> 주문_저장(GoodsOption 옵션, int 개수) {
        return HotSku폭주.주문_저장(orderRepository, 회원, 옵션, 개수);
    }
}

/**
 * 같은 폭주를 <b>선점 없이</b> 돌린다 — 1단계 강등 동작의 실측.
 *
 * <p>별도 클래스인 이유: {@code beautyboy.stock.admission}은 {@code @ConditionalOnProperty}로
 * 빈 구성 시점에 평가되므로, 켠 컨텍스트와 끈 컨텍스트가 물리적으로 다른 애플리케이션이어야 한다.
 *
 * <p>여기서 확인하려는 것은 "선점을 끄면 무엇을 잃는가"다. 초과 판매는 여전히 없지만
 * (DB가 막는다) 패자 전원이 토스 승인을 받고 곧바로 취소된다 — 설계 §2가 의도된 강등이라고
 * 적은 바로 그 동작이고, 2단계가 존재하는 이유를 수치로 보여주는 대조군이다.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "beautyboy.stock.admission=false")
class HotSkuAdmissionOffConcurrencyIT {

    @DynamicPropertySource
    static void 실_인프라로_바꿔_끼운다(DynamicPropertyRegistry registry) {
        HotSku컨테이너.등록한다(registry);
    }

    @TestConfiguration
    static class 가짜_게이트웨이_설정 {
        @Bean
        @Primary
        FakeCancelGateway fakeGateway() {
            return new FakeCancelGateway();
        }
    }

    @Autowired
    PaymentService paymentService;
    @Autowired
    FakeCancelGateway gateway;
    @Autowired
    MemberService memberService;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    GoodsOptionRepository goodsOptionRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    BrandRepository brandRepository;

    private Long 회원;

    @BeforeEach
    void 초기화() {
        gateway.reset();
        회원 = HotSku폭주.가입시킨다(memberService);
    }

    @Test
    @DisplayName("토글을 끄면 초과 판매는 여전히 없지만 패자 전원이 승인 후 취소된다")
    void 토글_끄면_선점없이도_초과판매는_없다() throws Exception {
        GoodsOption 옵션 = HotSku폭주.옵션_저장(brandRepository, goodsRepository, HotSkuConcurrencyIT.재고);
        List<Order> 주문들 = HotSku폭주.주문_저장(
                orderRepository, 회원, 옵션, HotSkuConcurrencyIT.동시_요청);

        List<Object> 결과 = HotSku폭주.동시에_승인한다(paymentService, 회원, 주문들);

        long 성공 = 결과.stream().filter(r -> !(r instanceof Throwable)).count();
        assertThat(성공).as("선점이 없어도 초과 판매는 DB가 막는다").isEqualTo(HotSkuConcurrencyIT.재고);
        assertThat(goodsOptionRepository.findById(옵션.getId()).orElseThrow().getStock()).isZero();
        assertThat(gateway.recorded())
                .as("패자 전원이 승인을 받았다가 취소됐다 — 이것이 선점이 닫는 창이다")
                .hasSize(HotSkuConcurrencyIT.동시_요청 - HotSkuConcurrencyIT.재고);
    }
}

/**
 * 두 컨텍스트가 공유하는 컨테이너. {@code @Container}가 아니라 정적 싱글턴인 이유는
 * 컨텍스트가 둘이기 때문이다 — JUnit 확장이 클래스마다 컨테이너를 새로 띄우면 두 번째
 * 컨텍스트가 다른 DB를 보게 되고, 기동 시간도 두 배가 된다. 정리는 Testcontainers의
 * Ryuk이 JVM 종료 후에 맡는다.
 */
final class HotSku컨테이너 {

    @SuppressWarnings("resource")
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    private HotSku컨테이너() {
    }

    static void 등록한다(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}

/** 두 클래스가 공유하는 폭주 픽스처. 단언은 각자 하고, 만들고 때리는 절차만 여기 모은다. */
final class HotSku폭주 {

    private HotSku폭주() {
    }

    /**
     * 전원을 같은 순간에 출발시킨다. 래치가 없으면 앞 스레드가 끝난 뒤 뒤 스레드가 시작해도
     * 단언이 통과해 — 폭주를 재현하지 않은 채 녹색이 된다.
     */
    static List<Object> 동시에_승인한다(PaymentService paymentService, Long 회원, List<Order> 주문들)
            throws Exception {
        CountDownLatch 출발 = new CountDownLatch(1);
        List<Callable<Object>> 일감 = new ArrayList<>();
        for (Order 주문 : 주문들) {
            PaymentConfirmRequest 요청 = new PaymentConfirmRequest(
                    주문.getOrderNo(), "pk_" + 주문.getOrderNo(), 주문.getPayableAmount());
            일감.add(() -> {
                출발.await(30, TimeUnit.SECONDS);
                try {
                    return paymentService.confirm(회원, 요청);
                } catch (Throwable t) {
                    return t;
                }
            });
        }

        ExecutorService 풀 = Executors.newFixedThreadPool(주문들.size());
        try {
            List<Future<Object>> 미래 = new ArrayList<>();
            for (Callable<Object> c : 일감) {
                미래.add(풀.submit(c));
            }
            출발.countDown();
            List<Object> 결과 = new ArrayList<>();
            for (Future<Object> f : 미래) {
                결과.add(f.get(120, TimeUnit.SECONDS));
            }
            return 결과;
        } finally {
            풀.shutdownNow();
        }
    }

    /** 실 MySQL에는 orders.member_id 외래키가 걸려 있어 임의의 id를 쓸 수 없다. */
    static Long 가입시킨다(MemberService memberService) {
        String 이메일 = "hot-sku-" + UUID.randomUUID().toString().substring(0, 8) + "@b.com";
        return memberService.signup(
                new SignupRequest(이메일, "pw123456", "폭주", null, null, null)).id();
    }

    static GoodsOption 옵션_저장(BrandRepository brandRepository, GoodsRepository goodsRepository,
                              int 재고) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        Goods goods = new Goods(brand, "C001001001", "한정판세럼", null, "https://img/x.jpg",
                20_000, HotSkuConcurrencyIT.단가);
        goods.getOptions().add(new GoodsOption(goods, "기본", 0, 재고, 0));
        return goodsRepository.saveAndFlush(goods).getOptions().get(0);
    }

    /** 회원 하나에 주문 여러 개. 주문 행 락은 주문마다 따로라 직렬화가 일어나지 않는다. */
    static List<Order> 주문_저장(OrderRepository orderRepository, Long 회원, GoodsOption 옵션,
                              int 개수) {
        List<Order> 주문들 = new ArrayList<>();
        for (int i = 0; i < 개수; i++) {
            Order order = new Order("ORD-" + System.nanoTime() + "-" + i, 회원, "홍길동",
                    "010-0000-0000", "06234", "서울시 강남구", "101호", "NORMAL", LocalDateTime.now());
            order.addItem(new OrderItem(옵션.getGoods().getId(), 옵션.getId(),
                    옵션.getGoods().getName(), 옵션.getName(), HotSkuConcurrencyIT.단가, 1));
            주문들.add(orderRepository.saveAndFlush(order));
        }
        return 주문들;
    }
}

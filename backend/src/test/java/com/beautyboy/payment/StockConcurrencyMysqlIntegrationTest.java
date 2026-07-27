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
import com.beautyboy.member.MemberService;
import com.beautyboy.member.dto.SignupRequest;
import com.beautyboy.order.OrderRepository;
import com.beautyboy.payment.dto.PaymentApproval;
import com.beautyboy.payment.dto.PaymentConfirmRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/**
 * 같은 옵션의 마지막 재고를 두 주문이 <b>동시에</b> 가져가려 할 때의 사양 — 이 태스크의 유일한 신뢰 근거.
 *
 * <p><b>왜 H2 쌍둥이를 만들지 않는가:</b> 여기서 지키려는 성질(조건부 UPDATE의 원자성, 늦은 쪽이
 * 행 잠금에서 대기했다가 영향 행 0을 받는 것)은 전적으로 InnoDB의 잠금 동작에 달려 있다.
 * H2(MVStore)는 잠금 타임아웃 예외를 던지는 등 동작이 달라 같은 단언이 통과해도 실 MySQL의
 * 근거가 되지 못한다 — 이 프로젝트에는 H2 녹색이 실 MySQL 결함을 가린 이력이 있으므로
 * 거짓 녹색이 무근거보다 위험하다. 그래서 실행분은 Testcontainers MySQL 8.4 하나뿐이다
 * (Flyway 실제 적용 + {@code ddl-auto=validate}, {@code AuthRefreshConcurrencyMysqlIntegrationTest}와 같은 기동).
 *
 * <p><b>왜 MockMvc가 아니라 {@link PaymentService}를 직접 부르는가:</b> 스레드마다 인증 컨텍스트를
 * 심고 지우는 관리 비용이 생기고, 그 관리가 어긋나면 레이스가 아니라 인증 실패를 보게 된다.
 * confirm의 트랜잭션 경계는 서비스에 있으므로 컨트롤러를 거칠 필요가 없다.
 *
 * <p><b>왜 클래스에 {@code @Transactional}이 없는가:</b> 두 스레드가 각자 진짜 트랜잭션을 커밋해야
 * 레이스가 성립한다. 테스트가 바깥에서 트랜잭션을 감싸면 서비스 호출이 거기에 참여해 경계가
 * 사라진다. 대신 데이터가 실제로 남으므로 {@link #뒷정리()}에서 직접 지운다.
 *
 * <p><b>레이스를 어떻게 진짜로 재현하는가:</b> 타이밍 운에 맡기지 않는다. {@link GoodsOptionRepository}를
 * spy로 감싸 {@code deduct}가 <b>실제 UPDATE를 실행하기 직전</b>에 {@link CyclicBarrier}에서 상대를
 * 기다리게 한다. 그래서 두 스레드는 반드시 "둘 다 재고를 깎기 직전이고 아직 아무도 깎지 않은"
 * 상태에서 출발하고, 늦은 쪽은 InnoDB 행 잠금에 실제로 걸린다. 배리어가 없으면 한쪽이 통째로
 * 끝난 뒤 다른 쪽이 시작해도 단언이 통과해 — 레이스를 검증하지 않은 채 녹색이 된다.
 *
 * <p>실행: {@code ./gradlew integrationTest}
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class StockConcurrencyMysqlIntegrationTest {

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

    private static final int 단가 = 16000;

    /**
     * 실 MySQL에는 {@code orders.member_id → member.id} 외래키가 실제로 걸려 있으므로
     * 임의의 회원 id를 쓰던 H2 테스트의 방식은 여기서 통하지 않는다 — 회원을 진짜로 가입시킨다
     * (Flyway 시드 회원과 겹치지 않도록 이메일에 UUID를 넣는다).
     */
    private Long 회원_갑;
    private Long 회원_을;

    /** 요청한 금액을 그대로 승인해 돌려준다 — 승인은 언제나 성공한다. 여기서 보려는 것은 재고뿐이다. */
    @TestConfiguration
    static class 가짜_게이트웨이_설정 {
        @Bean
        @Primary
        PaymentGateway fakeGateway() {
            return new PaymentGateway() {
                @Override
                public PaymentApproval confirm(String paymentKey, String orderNo, int amount) {
                    return new PaymentApproval(paymentKey, amount, "DONE", "{\"raw\":true}");
                }

                @Override
                public void cancel(String paymentKey, String reason) {
                }
            };
        }
    }

    /** 차감 직전에 두 스레드를 만나게 하려면 리포지토리를 감싸야 한다 — 프로덕션 코드에 훅을 심지 않기 위한 선택. */
    @MockitoSpyBean
    GoodsOptionRepository goodsOptionRepository;

    @Autowired
    PaymentService paymentService;
    @Autowired
    MemberService memberService;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    private final List<Long> 만든_주문 = new ArrayList<>();
    private final List<Long> 만든_상품 = new ArrayList<>();
    private final List<Long> 만든_브랜드 = new ArrayList<>();

    private final AtomicInteger 차감_시도_횟수 = new AtomicInteger();
    private CyclicBarrier 차감_직전_만남;

    @BeforeEach
    void 차감_직전_인터리빙을_강제한다() {
        회원_갑 = 가입시킨다("갑");
        회원_을 = 가입시킨다("을");

        차감_시도_횟수.set(0);
        차감_직전_만남 = new CyclicBarrier(2);

        // 왜 callRealMethod()가 아니라 "원래 기본 응답"에 위임하는가: GoodsOptionRepository는
        // 인터페이스이고 실제 빈은 Spring Data가 만든 프록시다. @MockitoSpyBean은 이 경우
        // 인터페이스 목을 만들고 기본 응답을 원본 빈 위임으로 걸어두므로, callRealMethod()는
        // 추상 메서드 호출로 즉시 터진다(AuthRefreshConcurrencyScenario에서 겪은 그대로).
        @SuppressWarnings("unchecked")
        Answer<Object> 원래_기본_응답 = (Answer<Object>) org.mockito.Mockito
                .mockingDetails(goodsOptionRepository).getMockCreationSettings().getDefaultAnswer();

        doAnswer(invocation -> {
            // 레이스에 참가하는 처음 두 번의 차감만 서로를 기다린다.
            // (레이스 이후의 순차 호출까지 기다리면 영원히 멈춘다.)
            if (차감_시도_횟수.incrementAndGet() <= 2) {
                차감_직전_만남.await(10, TimeUnit.SECONDS);
            }
            return 원래_기본_응답.answer(invocation);
        }).when(goodsOptionRepository).deduct(anyLong(), anyInt());
    }

    @AfterEach
    void 뒷정리() {
        List<Payment> 남은_결제 = paymentRepository.findAll().stream()
                .filter(p -> 만든_주문.contains(p.getOrderId()))
                .toList();
        paymentRepository.deleteAll(남은_결제);
        만든_주문.forEach(orderRepository::deleteById);
        만든_상품.forEach(goodsRepository::deleteById);
        만든_브랜드.forEach(brandRepository::deleteById);
        만든_주문.clear();
        만든_상품.clear();
        만든_브랜드.clear();
    }

    @Test
    void 마지막_재고_1개를_두_주문이_다투면_정확히_한쪽만_성공한다() throws Exception {
        GoodsOption 옵션 = 옵션_저장("마지막세럼", 1);
        Order 주문_갑 = 주문_저장(회원_갑, 옵션, 1);
        Order 주문_을 = 주문_저장(회원_을, 옵션, 1);

        List<Object> 결과 = 동시에_승인한다(주문_갑, 주문_을);

        assertThat(성공만(결과))
                .as("마지막 1개는 정확히 한쪽만 가져간다. 실제: %s", 결과)
                .hasSize(1);

        List<Throwable> 실패 = 실패만(결과);
        assertThat(실패).hasSize(1);
        assertThat(실패.get(0))
                .as("패배자는 미분류 예외(→500)가 아니라 BusinessException이어야 한다. 실제: %s", 실패.get(0))
                .isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) 실패.get(0)).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_OUT_OF_STOCK);

        // 재고는 0에서 멈춘다 — 음수는 이중 차감의 흔적이다.
        assertThat(재조회_재고(옵션)).isZero();
        assertThat(PAID_주문_수(주문_갑, 주문_을)).isEqualTo(1);
    }

    @Test
    void 재고_3을_두_주문_각_수량_2가_다투면_한쪽만_성공하고_재고_1이_남는다() throws Exception {
        // 경계값(부분 충족): 합계 4는 재고 3을 넘지만 각 주문은 단독으로는 통과한다.
        // 조건부 UPDATE가 원자가 아니면 둘 다 성공해 재고가 -1이 된다.
        GoodsOption 옵션 = 옵션_저장("경계크림", 3);
        Order 주문_갑 = 주문_저장(회원_갑, 옵션, 2);
        Order 주문_을 = 주문_저장(회원_을, 옵션, 2);

        List<Object> 결과 = 동시에_승인한다(주문_갑, 주문_을);

        assertThat(성공만(결과))
                .as("재고 3에서 수량 2를 둘 다 가져갈 수는 없다. 실제: %s", 결과)
                .hasSize(1);

        List<Throwable> 실패 = 실패만(결과);
        assertThat(실패).hasSize(1);
        assertThat(실패.get(0))
                .as("패배자는 미분류 예외(→500)가 아니라 BusinessException이어야 한다. 실제: %s", 실패.get(0))
                .isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) 실패.get(0)).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_OUT_OF_STOCK);

        assertThat(재조회_재고(옵션)).isEqualTo(1);
        assertThat(PAID_주문_수(주문_갑, 주문_을)).isEqualTo(1);
    }

    /** 두 스레드가 각자의 주문을 동시에 승인한다. 예외는 던지지 않고 결과로 수집한다. */
    private List<Object> 동시에_승인한다(Order 갑의_주문, Order 을의_주문) throws Exception {
        Callable<Object> 갑 = 한_번_승인(회원_갑, 갑의_주문);
        Callable<Object> 을 = 한_번_승인(회원_을, 을의_주문);

        ExecutorService 풀 = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> 미래 = 풀.invokeAll(List.of(갑, 을));
            List<Object> 결과 = new ArrayList<>();
            for (Future<Object> f : 미래) {
                결과.add(f.get(30, TimeUnit.SECONDS));
            }
            return 결과;
        } finally {
            풀.shutdownNow();
        }
    }

    private Callable<Object> 한_번_승인(Long 회원, Order 주문) {
        // 스레드마다 서비스의 @Transactional이 새 트랜잭션을 연다 — 별도 준비가 필요 없다.
        PaymentConfirmRequest 요청 = new PaymentConfirmRequest(
                주문.getOrderNo(), "pk_" + 주문.getOrderNo(), 주문.getPayableAmount());
        return () -> {
            try {
                return paymentService.confirm(회원, 요청);
            } catch (Throwable t) {
                return t;
            }
        };
    }

    private List<Object> 성공만(List<Object> 결과) {
        return 결과.stream().filter(r -> !(r instanceof Throwable)).toList();
    }

    private List<Throwable> 실패만(List<Object> 결과) {
        return 결과.stream()
                .filter(Throwable.class::isInstance)
                .map(Throwable.class::cast)
                .toList();
    }

    private long PAID_주문_수(Order... 주문들) {
        return java.util.Arrays.stream(주문들)
                .map(o -> orderRepository.findByOrderNo(o.getOrderNo()).orElseThrow().getStatus())
                .filter(Order.STATUS_PAID::equals)
                .count();
    }

    private int 재조회_재고(GoodsOption 옵션) {
        // 테스트 메서드가 트랜잭션이 아니므로 이 조회는 새 트랜잭션에서 DB의 현재 값을 본다.
        return goodsOptionRepository.findById(옵션.getId()).orElseThrow().getStock();
    }

    private Long 가입시킨다(String 이름) {
        String 이메일 = "stock-race-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@b.com";
        return memberService.signup(new SignupRequest(이메일, "pw123456", 이름, null, null, null)).id();
    }

    private Order 주문_저장(Long 회원, GoodsOption 옵션, int 수량) {
        Order order = new Order("ORD-" + System.nanoTime(), 회원, "홍길동", "010-0000-0000",
                "06234", "서울시 강남구", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(옵션.getGoods().getId(), 옵션.getId(),
                옵션.getGoods().getName(), 옵션.getName(), 단가, 수량));
        Order saved = orderRepository.saveAndFlush(order);
        만든_주문.add(saved.getId());
        return saved;
    }

    /** 브랜드·상품·옵션 하나를 커밋한다(테스트가 비트랜잭션이라 그 자리에서 커밋된다). */
    private GoodsOption 옵션_저장(String 이름, int 재고) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        만든_브랜드.add(brand.getId());
        Goods goods = new Goods(brand, "C001001001", 이름, null, "https://img/x.jpg", 20000, 단가);
        goods.getOptions().add(new GoodsOption(goods, "기본", 0, 재고, 0));
        Goods saved = goodsRepository.saveAndFlush(goods);
        만든_상품.add(saved.getId());
        return saved.getOptions().get(0);
    }
}

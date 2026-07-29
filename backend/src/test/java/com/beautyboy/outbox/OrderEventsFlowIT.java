package com.beautyboy.outbox;

import com.beautyboy.cart.CartService;
import com.beautyboy.cart.dto.CartAddRequest;
import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsOption;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.member.MemberService;
import com.beautyboy.member.dto.SignupRequest;
import com.beautyboy.notification.NotificationRepository;
import com.beautyboy.order.Order;
import com.beautyboy.order.OrderRepository;
import com.beautyboy.order.OrderService;
import com.beautyboy.order.dto.OrderCreateRequest;
import com.beautyboy.order.dto.OrderCreateResponse;
import com.beautyboy.payment.PaymentGateway;
import com.beautyboy.payment.PaymentService;
import com.beautyboy.payment.dto.PaymentApproval;
import com.beautyboy.payment.dto.PaymentConfirmRequest;
import com.beautyboy.ranking.GoodsDailyStat;
import com.beautyboy.ranking.GoodsDailyStatRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * 세트 A의 유일한 실브로커 종단 검증(A7, 계획서 Task A7). MySQL + Kafka Testcontainers로
 * 확정 → 아웃박스 → 릴레이 → Kafka → 컨슈머 3종(cart-clear · sales-aggregation · notification)
 * 전 구간과, DLQ 이동 · replay 재처리를 실제 브로커를 거쳐 확인한다.
 *
 * <p><b>왜 여기서만 실브로커인가</b>: Global Constraints가 {@code test}는 Docker/브로커 없이
 * 녹색이어야 한다고 못박았다. 그래서 A4~A6는 컨슈머 메서드 직접 호출이나 Kafka 관련 컴포넌트
 * 목으로 {@code test}에서 돌았고({@link PostOrderConsumersTest}, {@link OutboxRelayTest},
 * {@link DlqReplayServiceTest}), "컨슈머 그룹을 셋으로 가른 결정이 실제로 팬아웃되는가",
 * "DLT 재처리가 실제 오프셋 커밋으로 두 번째 호출에 0을 돌려주는가" 같은 질문은 실브로커가
 * 있어야 답이 된다 — 그 답을 이 클래스 하나에 모은다.
 *
 * <p><b>{@code beautyboy.events.enabled=true}가 이 테스트의 핵심이다</b>: {@code test} 프로필은
 * 이 토글이 꺼져 있어 {@link OutboxRelay}·{@link KafkaTopicConfig}·{@link KafkaConsumerConfig}가
 * 구성하는 컨슈머 컨테이너 자동시작·{@link DlqReplayService}·{@link DlqReplayController}가
 * 지금까지 단 한 번도 켜진 채로 컨텍스트를 띄운 적이 없다. 이 클래스가 그 배선을 처음 검증한다.
 *
 * <p><b>격리 전략</b>: 컨슈머 3종이 각자 다른 그룹으로 클래스 전체에서 계속 살아 돈다(같은
 * {@code order-events} 토픽을 여러 테스트가 공유). 그래서
 * <ol>
 *   <li>{@link TestMethodOrder}로 실행 순서를 고정하고({@link Order}), DLT를 만드는 테스트(3~5)는
 *       각자 스스로 만든 DLT 레코드를 스스로 {@link DlqReplayService#replay()}로 비운 뒤 끝난다 —
 *       다음 테스트가 깨끗한 DLT에서 시작한다.</li>
 *   <li>테스트마다 <b>다른 회원·다른 상품</b>을 새로 만든다(회원가입 이메일에 UUID, 상품명에
 *       테스트 태그) — 같은 픽스처를 공유하면 앞 테스트의 부작용이 뒤로 샌다.</li>
 *   <li>판매 집계·알림 개수 단언은 <b>그 테스트가 만든 상품/회원 기준의 절대값</b>으로 본다
 *       (상품별 {@code goods_daily_stat} 행이 테스트마다 새로 생기므로 절대값이 곧 증분이다) —
 *       "이전 값 + 기대 증분"을 따로 계산할 필요가 없어 계산 실수로 인한 거짓 녹색을 피한다.</li>
 *   <li>알림 실패 유도(3~5)는 {@link NotificationRepository}를 {@link MockitoSpyBean}으로 감싸
 *       <b>회원 id가 실패 대상 집합에 있을 때만</b> 예외를 던진다 — 다른 테스트의 회원은 절대
 *       건드리지 않으므로 스파이가 클래스 전체에서 하나만 있어도 테스트 간 오염이 없다.</li>
 * </ol>
 *
 * <p><b>컨슈머 실패 유발 방법과 근거</b>: 계획서는 "member 삭제 등"을 예로 들었지만
 * {@code notification.member_id}에는 FK가 없다(V92 DDL 참고 — 타 도메인 참조를 스칼라로 남긴
 * 설계라 제약도 걸지 않았다). {@code message} 컬럼(VARCHAR(200))도 orderNo가 최대 30자라
 * 넘칠 수 없다. 그래서 {@link NotificationRepository#insertIfAbsent}를 스파이로 감싸 특정
 * 회원 id에 대해서만 {@link RuntimeException}을 던지는 방식을 썼다 — 이미 {@code StockConcurrencyMysqlIntegrationTest}가
 * {@link GoodsOptionRepository}에 쓴 것과 같은 도구(MockitoSpyBean + 원본 위임 doAnswer)라
 * 새로운 위험을 들이지 않는다.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class OrderEventsFlowIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    /**
     * A1의 compose는 {@code apache/kafka:3.9.0}(공식 JVM 이미지)을 직접 env var로 구성했지만,
     * 그 이미지를 신형 {@code org.testcontainers.kafka.KafkaContainer}에 그대로 물리면 그 클래스가
     * 기대하는 엔트리포인트 스크립트 버전과 어긋나 {@code advertised.listeners}가 0.0.0.0으로
     * 남는 채 기동이 실패한다(실측 — 브로커 로그: {@code requirement failed: advertised.listeners
     * cannot use the nonroutable meta-address 0.0.0.0}). 그래서 여기서는 Testcontainers가 오래
     * 검증해 온 조합인 구 {@code org.testcontainers.containers.KafkaContainer} +
     * {@code confluentinc/cp-kafka}(KRaft)로 바꿨다 — 검증 대상(팬아웃 컨슈머·DLT·릴레이)은
     * 브로커 이미지 종류와 무관하다.
     */
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.1"))
            .withKraft();

    @DynamicPropertySource
    static void 인프라를_실컨테이너로_연결한다(DynamicPropertyRegistry registry) {
        // useAffectedRows=true는 프로덕션 URL(application.yml)과 같은 이유로 필요하다 — 이 파라미터가
        // 없으면 outbox의 멱등성 게이트(processed_event/notification의 "영향 행 0 == 중복" 판정)가
        // 실 MySQL에서 조용히 깨진다(이 클래스가 실측한 실제 결함, KafkaConsumerConfig 근처 커밋 참고).
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?useAffectedRows=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // 이 토글이 이 테스트의 핵심이다 — OutboxRelay·KafkaTopicConfig·KafkaConsumerConfig·
        // DlqReplayService·DlqReplayController가 test 프로필에서는 한 번도 뜬 적이 없다.
        registry.add("beautyboy.events.enabled", () -> true);
        // 릴레이 주기 1초를 300ms로 낮춰 대기 시간을 줄인다(계획서 "판단이 필요한 지점들" 허용).
        registry.add("beautyboy.events.relay-delay-ms", () -> "300");
    }

    private static final int 단가 = 15000;
    private static final Duration 일반_대기 = Duration.ofSeconds(10);
    /** 컨슈머 재시도 백오프(1s→2s→4s=7s 고정, KafkaConsumerConfig)를 감안한 DLT 대기 상한. */
    private static final Duration DLT_대기 = Duration.ofSeconds(20);

    /** 토스를 절대 부르지 않는다 — 요청 금액을 그대로 승인하는 가짜 게이트웨이로 대체한다
     * (StockConcurrencyMysqlIntegrationTest와 같은 패턴). */
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

    @Autowired
    PaymentService paymentService;
    @Autowired
    OrderService orderService;
    @Autowired
    MemberService memberService;
    @Autowired
    CartService cartService;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    GoodsDailyStatRepository goodsDailyStatRepository;
    @Autowired
    OutboxEventRepository outboxEventRepository;
    @Autowired
    DlqReplayService dlqReplayService;
    @Autowired
    JdbcTemplate jdbcTemplate;

    /** notification 적재를 가로챌 스파이. 실패 대상 회원 id일 때만 예외를 던지고, 그 외는 원본에 위임한다. */
    @MockitoSpyBean
    NotificationRepository notificationRepository;

    /**
     * {@link #notificationRepository} 스텁이 예외를 던져야 하는 회원 id 집합. 테스트마다 자기 것만
     * 넣고 뺀다. {@code static}인 이유: {@code @Testcontainers} + {@code @DynamicPropertySource}
     * 조합은 {@code @TestInstance(PER_CLASS)}와 함께 쓰면 컨테이너가 뜨기 전에 Spring 컨텍스트가
     * 먼저 로드되는 순서 문제가 있어(포트 미확정 예외) 기본 PER_METHOD 라이프사이클을 쓴다 —
     * 그러면 테스트 메서드마다 인스턴스가 새로 생기므로 인스턴스 필드로는 순서 간 상태 공유가 안 된다.
     */
    private static final Set<Long> 알림_실패_대상_회원 = ConcurrentHashMap.newKeySet();

    /**
     * 매 테스트 메서드 실행 전 스텁을 (재)적용한다. 스파이 대상 빈 자체는 캐시된 스프링 컨텍스트에
     * 계속 살아 있는 싱글턴이라 매번 새로 걸어도 이전 동작을 덮어쓸 뿐 부작용이 없다 —
     * {@code @BeforeAll}을 못 쓰는 이유(위 필드 주석)의 직접적인 대안이다.
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void 알림_실패_스텁을_구성한다() {
        // 원래 기본 응답(원본 빈 위임) — StockConcurrencyMysqlIntegrationTest의 같은 트릭.
        // NotificationRepository도 Spring Data 프록시 인터페이스라 callRealMethod()가 아니라
        // 이 위임을 통해야 실제 insertIfAbsent SQL이 나간다.
        Answer<Object> 원래_기본_응답 = (Answer<Object>) org.mockito.Mockito
                .mockingDetails(notificationRepository).getMockCreationSettings().getDefaultAnswer();

        doAnswer(invocation -> {
            Long memberId = invocation.getArgument(0);
            if (알림_실패_대상_회원.contains(memberId)) {
                throw new RuntimeException("의도된 알림 실패(A7 컨슈머 DLT 픽스처): memberId=" + memberId);
            }
            return 원래_기본_응답.answer(invocation);
        }).when(notificationRepository)
                .insertIfAbsent(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    // ── 케이스 1 ──────────────────────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(1)
    void 확정하면_아웃박스를_거쳐_컨슈머_3종이_모두_처리한다() {
        Long 회원 = 회원가입("case1");
        Goods 상품 = 상품_저장("토너-case1", 50);

        OrderCreateResponse 주문 = 주문_생성(회원, 상품, 2);
        paymentService.confirm(회원, new PaymentConfirmRequest(
                주문.orderNo(), "pk_" + 주문.orderNo(), 주문.payableAmount()));

        Long orderId = orderRepository.findByOrderNo(주문.orderNo()).orElseThrow().getId();

        await().atMost(일반_대기).untilAsserted(() -> {
            // 장바구니: 주문한 상품이 지워졌다(cart-clear 컨슈머).
            assertThat(cartService.itemsOf(회원)).isEmpty();

            // 판매 집계: 수량 2만큼 늘었다(sales-aggregation 컨슈머). 이 테스트가 만든 상품이라
            // 절대값 == 이 테스트의 증분이다.
            assertThat(판매수량(상품)).isEqualTo(2);

            // 알림: 정확히 1건(notification 컨슈머).
            assertThat(notificationRepository.findByMemberId(회원)).hasSize(1);

            // 아웃박스: 릴레이가 발행 성공을 확인하고 PUBLISHED로 마킹했다.
            OutboxEvent event = 주문의_아웃박스_행(orderId);
            assertThat(event.getStatus()).isEqualTo(OutboxEvent.STATUS_PUBLISHED);
        });
    }

    // ── 케이스 2 ──────────────────────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(2)
    void 릴레이가_같은_이벤트를_재발행해도_집계와_알림은_한번만_반영된다() {
        Long 회원 = 회원가입("case2");
        Goods 상품 = 상품_저장("토너-case2", 50);

        OrderCreateResponse 주문 = 주문_생성(회원, 상품, 3);
        paymentService.confirm(회원, new PaymentConfirmRequest(
                주문.orderNo(), "pk_" + 주문.orderNo(), 주문.payableAmount()));
        Long orderId = orderRepository.findByOrderNo(주문.orderNo()).orElseThrow().getId();

        // 최초 처리 완료를 먼저 확인한다 — 이게 안 되면 재발행 검증 자체가 무의미하다.
        await().atMost(일반_대기).untilAsserted(() -> {
            assertThat(판매수량(상품)).isEqualTo(3);
            assertThat(notificationRepository.findByMemberId(회원)).hasSize(1);
            assertThat(주문의_아웃박스_행(orderId).getStatus()).isEqualTo(OutboxEvent.STATUS_PUBLISHED);
        });

        // 강제 재발행: PUBLISHED 행을 PENDING으로 되돌린다 — 릴레이가 다음 주기에 다시 집어 발행한다.
        // (설계상 정상 경로도 이런 재발행 창을 만든다: 릴레이가 send 성공 후 마킹 직전에 죽으면
        //  다음 재시작 때 같은 일이 벌어진다 — 그래서 컨슈머 멱등성이 필요하다는 것이 이 테스트의 요지다.)
        Long 아웃박스_id = 주문의_아웃박스_행(orderId).getId();
        jdbcTemplate.update(
                "update outbox_event set status = 'PENDING', published_at = null where id = ?", 아웃박스_id);

        // 재발행됐다는 신호(다시 PUBLISHED로 마킹됨)를 기다린다.
        await().atMost(일반_대기).untilAsserted(() ->
                assertThat(outboxEventRepository.findById(아웃박스_id).orElseThrow().getStatus())
                        .isEqualTo(OutboxEvent.STATUS_PUBLISHED));

        // 재발행된 메시지를 컨슈머가 처리할 짧은 여유를 둔 뒤, 멱등성 게이트(processed_event /
        // uk_notification_dedup)가 중복 반영을 막았는지 확인한다. await 대신 고정 대기 + 단발
        // 단언을 쓰는 이유: "값이 끝까지 그대로였는가"가 아니라 "재처리가 끝난 뒤에도 여전히
        // 1건/3개인가"만 보면 되고, 그 편이 폴링 조건보다 이 시나리오의 의도를 더 직접적으로 드러낸다.
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(판매수량(상품)).isEqualTo(3);
        assertThat(notificationRepository.findByMemberId(회원)).hasSize(1);
    }

    // ── 케이스 3 ──────────────────────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(3)
    void 컨슈머_예외가_계속되면_DLT로_가고_replay로_복구된다() {
        Long 회원 = 회원가입("case3");
        알림_실패_대상_회원.add(회원);
        try {
            Goods 상품 = 상품_저장("토너-case3", 50);
            OrderCreateResponse 주문 = 주문_생성(회원, 상품, 1);
            paymentService.confirm(회원, new PaymentConfirmRequest(
                    주문.orderNo(), "pk_" + 주문.orderNo(), 주문.payableAmount()));
            Order order = orderRepository.findByOrderNo(주문.orderNo()).orElseThrow();

            // notification 컨슈머는 재시도 3회(1s→2s→4s)를 소진하고 order-events.DLT로 빠진다.
            // cart-clear·sales-aggregation은 서로 다른 그룹이라 이 실패에 영향받지 않고 정상 처리된다.
            await().atMost(DLT_대기).untilAsserted(() ->
                    assertThat(DLT에_키가_있다(order.getId().toString())).isTrue());

            // 그동안 알림은 한 건도 안 생겼어야 한다 — 재시도 중엔 계속 실패했으니까.
            assertThat(notificationRepository.findByMemberId(회원)).isEmpty();
            // 다른 두 컨슈머는 이 실패와 무관하게 끝났다 — 그룹을 가른 A5의 결정이 여기서 증명된다.
            assertThat(cartService.itemsOf(회원)).isEmpty();
            assertThat(판매수량(상품)).isEqualTo(1);

            // 원인을 복구한다.
            알림_실패_대상_회원.remove(회원);

            int replayed = dlqReplayService.replay();
            assertThat(replayed).isGreaterThanOrEqualTo(1);

            // 재발행된 이벤트가 이번엔 성공해 알림이 만들어진다.
            await().atMost(일반_대기).untilAsserted(() ->
                    assertThat(notificationRepository.findByMemberId(회원)).hasSize(1));

            // replay는 order-events로 재발행하므로 cart-clear·sales-aggregation도 다시 받는다 —
            // 각자의 멱등성(자연 멱등 / processed_event)이 이중 반영을 막는다는 것도 함께 확인한다.
            assertThat(cartService.itemsOf(회원)).isEmpty();
            assertThat(판매수량(상품)).isEqualTo(1);
        } finally {
            알림_실패_대상_회원.remove(회원);
        }
    }

    // ── 케이스 4 ──────────────────────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(4)
    void 컨슈머가_실패해도_다음_메시지는_계속_소비된다() {
        Long 실패할_회원 = 회원가입("case4-fail");
        Long 정상_회원 = 회원가입("case4-ok");
        알림_실패_대상_회원.add(실패할_회원);
        try {
            Goods 실패_상품 = 상품_저장("토너-case4f", 50);
            OrderCreateResponse 실패_주문 = 주문_생성(실패할_회원, 실패_상품, 1);
            paymentService.confirm(실패할_회원, new PaymentConfirmRequest(
                    실패_주문.orderNo(), "pk_" + 실패_주문.orderNo(), 실패_주문.payableAmount()));

            // 실패할_회원의 메시지가 재시도(최대 7초)를 도는 동안 — 아직 DLT로 빠지기 전에 —
            // 곧바로 다른 주문을 확정한다. 이 메시지가 늦게라도 정상 처리되면, 앞선 실패가
            // 컨슈머(그 파티션의 poll 루프)를 영영 막지 않는다는 뜻이다.
            Goods 정상_상품 = 상품_저장("토너-case4o", 50);
            OrderCreateResponse 정상_주문 = 주문_생성(정상_회원, 정상_상품, 1);
            paymentService.confirm(정상_회원, new PaymentConfirmRequest(
                    정상_주문.orderNo(), "pk_" + 정상_주문.orderNo(), 정상_주문.payableAmount()));

            await().atMost(일반_대기).untilAsserted(() ->
                    assertThat(notificationRepository.findByMemberId(정상_회원)).hasSize(1));

            // 뒷정리 겸 사양 재확인: 실패한 메시지는 결국 재시도를 소진하고 DLT로 빠진다 —
            // 다음 테스트가 깨끗한 DLT에서 시작하도록 여기서 직접 복구한다.
            Order 실패_주문_행 = orderRepository.findByOrderNo(실패_주문.orderNo()).orElseThrow();
            await().atMost(DLT_대기).untilAsserted(() ->
                    assertThat(DLT에_키가_있다(실패_주문_행.getId().toString())).isTrue());

            알림_실패_대상_회원.remove(실패할_회원);
            int replayed = dlqReplayService.replay();
            assertThat(replayed).isGreaterThanOrEqualTo(1);

            await().atMost(일반_대기).untilAsserted(() ->
                    assertThat(notificationRepository.findByMemberId(실패할_회원)).hasSize(1));
        } finally {
            알림_실패_대상_회원.remove(실패할_회원);
        }
    }

    // ── 케이스 5 ──────────────────────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(5)
    void DlqReplayService_replay는_두번째_호출에서_0을_반환한다() {
        Long 회원 = 회원가입("case5");
        알림_실패_대상_회원.add(회원);
        try {
            Goods 상품 = 상품_저장("토너-case5", 50);
            OrderCreateResponse 주문 = 주문_생성(회원, 상품, 1);
            paymentService.confirm(회원, new PaymentConfirmRequest(
                    주문.orderNo(), "pk_" + 주문.orderNo(), 주문.payableAmount()));
            Order order = orderRepository.findByOrderNo(주문.orderNo()).orElseThrow();

            await().atMost(DLT_대기).untilAsserted(() ->
                    assertThat(DLT에_키가_있다(order.getId().toString())).isTrue());

            알림_실패_대상_회원.remove(회원);

            // 첫 호출: 그룹 dlq-replay가 커밋된 오프셋이 없어 DLT를 처음부터 읽는다 — 이번에
            // 쌓인 레코드를 전부 재발행한다.
            int 첫_번째_호출 = dlqReplayService.replay();
            assertThat(첫_번째_호출).isGreaterThanOrEqualTo(1);

            // 두 번째 호출: 같은 그룹이 방금 커밋한 오프셋 뒤부터 이어 읽으므로 새 DLT 레코드가
            // 없으면 즉시 빈 poll이다 — DlqReplayService 오프셋 전략(커밋 기반 "이어서 읽기")의 핵심 단언.
            int 두_번째_호출 = dlqReplayService.replay();
            assertThat(두_번째_호출).isZero();

            await().atMost(일반_대기).untilAsserted(() ->
                    assertThat(notificationRepository.findByMemberId(회원)).hasSize(1));
        } finally {
            알림_실패_대상_회원.remove(회원);
        }
    }

    // ── 픽스처 헬퍼 ────────────────────────────────────────────────────────

    private Long 회원가입(String 태그) {
        String 이메일 = "order-events-" + 태그 + "-" + UUID.randomUUID().toString().substring(0, 8) + "@b.com";
        return memberService.signup(new SignupRequest(이메일, "pw123456", 태그, null, null, null)).id();
    }

    private Goods 상품_저장(String 이름, int 재고) {
        Brand brand = brandRepository.save(new Brand("브랜드-" + 이름 + "-" + System.nanoTime(), null));
        Goods goods = new Goods(brand, "C001001001", 이름, null, "https://img/x.jpg", 20000, 단가);
        goods.getOptions().add(new GoodsOption(goods, "기본", 0, 재고, 0));
        return goodsRepository.saveAndFlush(goods);
    }

    private OrderCreateResponse 주문_생성(Long 회원, Goods 상품, int 수량) {
        Long 옵션 = 상품.getOptions().get(0).getId();
        cartService.add(회원, new CartAddRequest(상품.getId(), 옵션, 수량));
        return orderService.create(회원, new OrderCreateRequest(
                List.of(new OrderCreateRequest.OrderItemRequest(상품.getId(), 옵션, 수량)),
                "홍길동", "010-0000-0000", "06234", "서울시 강남구", "101호", "NORMAL"));
    }

    private int 판매수량(Goods 상품) {
        return goodsDailyStatRepository
                .findById(new GoodsDailyStat.Key(상품.getId(), LocalDate.now()))
                .map(GoodsDailyStat::getSalesCount)
                .orElse(0);
    }

    private OutboxEvent 주문의_아웃박스_행(Long orderId) {
        return outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(orderId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("orderId=" + orderId + "의 아웃박스 행이 없다"));
    }

    /**
     * {@code order-events.DLT}에 주어진 키(주문 id 문자열)를 가진 레코드가 있는지 확인한다.
     * 매번 새 그룹 id로 처음부터(earliest) 읽고 커밋하지 않는다 — {@link DlqReplayService}의
     * {@code dlq-replay} 그룹 오프셋에 전혀 영향을 주지 않는 순수 관찰용이다.
     */
    private boolean DLT에_키가_있다(String key) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-dlt-observer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(OutboxRelay.TOPIC + ".DLT"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(3));
            for (ConsumerRecord<String, String> record : records) {
                if (key.equals(record.key())) {
                    return true;
                }
            }
            return false;
        }
    }
}

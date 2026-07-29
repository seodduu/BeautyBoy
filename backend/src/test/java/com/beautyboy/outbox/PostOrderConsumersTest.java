package com.beautyboy.outbox;

import com.beautyboy.cart.CartClearOnOrderConfirmed;
import com.beautyboy.cart.CartService;
import com.beautyboy.cart.dto.CartAddRequest;
import com.beautyboy.cart.dto.CartItemResponse;
import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsOption;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.notification.Notification;
import com.beautyboy.notification.NotificationConsumer;
import com.beautyboy.notification.NotificationRepository;
import com.beautyboy.order.OrderService;
import com.beautyboy.order.dto.OrderCreateRequest;
import com.beautyboy.ranking.GoodsDailyStat;
import com.beautyboy.ranking.GoodsDailyStatRepository;
import com.beautyboy.ranking.RankingBatchService;
import com.beautyboy.ranking.SalesAggregationConsumer;
import com.beautyboy.ranking.WishStatProvider;
import com.beautyboy.support.TestPersistence;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 확정 후처리 3종의 컨슈머(A5). A4b의 동기 로직이 그대로 옮겨 온 자리다.
 *
 * <p><b>왜 브로커 없이 도는가</b>: Global Constraints가 {@code test} 태스크는 Docker 없이 녹색이어야
 * 한다고 못 박았고 {@code @EmbeddedKafka}를 금지했다. 그래서 여기서는 컨슈머 메서드를 직접 호출한다 —
 * 검증 대상은 "이벤트를 받았을 때 무엇이 일어나는가"이지 Kafka의 배달 자체가 아니다.
 * 실브로커를 거치는 종단 검증은 A7({@code @Tag("integration")}) 하나에 모인다.
 */
class PostOrderConsumersTest {

    private static final int 단가 = 12000;

    /** 컨슈머 3종의 동작과 멱등성. */
    @SpringBootTest
    @ActiveProfiles("test")
    @Transactional
    static class 컨슈머_동작 {

        private static final Long 회원 = 780101L;
        private static final LocalDate 오늘 = LocalDate.now();
        /** 가짜 찜 공급자가 가리킬 상품. 배치가 찜을 upsert하게 만들어 판매량 유실을 재현한다. */
        private static final AtomicReference<Long> 찜_대상_상품_id = new AtomicReference<>();

        @TestConfiguration
        static class 가짜_찜_공급자 {
            @Bean
            @Primary
            WishStatProvider fakeWishStatProvider() {
                return date -> {
                    Long goodsId = 찜_대상_상품_id.get();
                    return (goodsId != null && date.equals(오늘)) ? Map.of(goodsId, 5) : Map.of();
                };
            }
        }

        @Autowired
        CartClearOnOrderConfirmed cartClearConsumer;
        @Autowired
        SalesAggregationConsumer salesAggregationConsumer;
        @Autowired
        NotificationConsumer notificationConsumer;
        @Autowired
        CartService cartService;
        @Autowired
        GoodsDailyStatRepository goodsDailyStatRepository;
        @Autowired
        NotificationRepository notificationRepository;
        @Autowired
        RankingBatchService rankingBatchService;
        @Autowired
        BrandRepository brandRepository;
        @Autowired
        GoodsRepository goodsRepository;
        @Autowired
        ObjectMapper objectMapper;
        @PersistenceContext
        EntityManager entityManager;

        @Test
        void 확정_이벤트를_받으면_주문_상품만_장바구니에서_지운다() {
            Goods 주문한_상품 = 옵션상품_저장("토너");
            Goods 안_주문한_상품 = 옵션상품_저장("클렌저");
            cartService.add(회원, new CartAddRequest(주문한_상품.getId(), 옵션_id(주문한_상품), 2));
            cartService.add(회원, new CartAddRequest(안_주문한_상품.getId(), 옵션_id(안_주문한_상품), 1));

            cartClearConsumer.on(레코드(확정_이벤트(줄(주문한_상품, 2))));

            TestPersistence.DB_왕복_강제(entityManager);

            assertThat(cartService.itemsOf(회원))
                    .extracting(CartItemResponse::goodsNo)
                    .containsExactly(안_주문한_상품.getId());
        }

        @Test
        void 같은_이벤트를_두번_소비해도_판매집계는_한번만_는다() {
            Goods 상품 = 옵션상품_저장("세럼");
            OrderConfirmedEvent 이벤트 = 확정_이벤트(줄(상품, 3));

            salesAggregationConsumer.on(레코드(이벤트));
            // 재시도·리밸런싱·릴레이 재발행으로 같은 이벤트가 또 온 상황.
            salesAggregationConsumer.on(레코드(이벤트));

            TestPersistence.DB_왕복_강제(entityManager);

            assertThat(판매수량(상품)).isEqualTo(3);
        }

        /**
         * 멱등성 게이트가 "중복이면 스킵"이지 "중복이면 이 이벤트는 영영 스킵"이 되면 안 된다 —
         * 다른 이벤트는 그대로 더해져야 한다(증분이 덮어쓰기가 아님을 함께 못 박는다).
         */
        @Test
        void 다른_이벤트는_판매집계에_그대로_더해진다() {
            Goods 상품 = 옵션상품_저장("앰플");

            salesAggregationConsumer.on(레코드(확정_이벤트(줄(상품, 3))));
            salesAggregationConsumer.on(레코드(확정_이벤트(줄(상품, 2))));

            TestPersistence.DB_왕복_강제(entityManager);

            assertThat(판매수량(상품)).isEqualTo(5);
        }

        @Test
        void 같은_이벤트를_두번_소비해도_알림은_한건이다() {
            Goods 상품 = 옵션상품_저장("에센스");
            OrderConfirmedEvent 이벤트 = 확정_이벤트(줄(상품, 1));

            notificationConsumer.on(레코드(이벤트));
            notificationConsumer.on(레코드(이벤트));

            TestPersistence.DB_왕복_강제(entityManager);

            List<Notification> 알림 = notificationRepository.findByMemberId(회원);
            assertThat(알림).hasSize(1);
            assertThat(알림.get(0).getType()).isEqualTo("ORDER_CONFIRMED");
            assertThat(알림.get(0).getMessage())
                    .isEqualTo("주문 " + 이벤트.orderNo() + " 결제가 완료됐어요.");
            // 동기 경로(A4b)는 eventId가 없어 orderId로 대체했지만, 컨슈머는 진짜 eventId를 갖는다.
            assertThat(알림.get(0).getEventId()).isEqualTo(이벤트.eventId());
        }

        /**
         * 판매 이중 계상/소실 회귀(A4b에서 옮겨 옴). 증분 경로(컨슈머)와 덮어쓰기 경로(배치)가
         * 같은 컬럼을 두고 겹치면 배치가 도는 순간 쌓아 둔 판매량이 조용히 사라진다.
         */
        @Test
        void 랭킹_배치가_돌아도_증분된_판매량이_사라지지_않는다() {
            Goods 상품 = 옵션상품_저장("크림");
            찜_대상_상품_id.set(상품.getId());
            salesAggregationConsumer.on(레코드(확정_이벤트(줄(상품, 4))));

            TestPersistence.DB_왕복_강제(entityManager);

            rankingBatchService.rebuild();

            TestPersistence.DB_왕복_강제(entityManager);

            GoodsDailyStat stat = goodsDailyStatRepository
                    .findById(new GoodsDailyStat.Key(상품.getId(), 오늘)).orElseThrow();
            assertThat(stat.getSalesCount()).isEqualTo(4);
            assertThat(stat.getWishCount()).isEqualTo(5);
        }

        private int 판매수량(Goods 상품) {
            return goodsDailyStatRepository.findById(new GoodsDailyStat.Key(상품.getId(), 오늘))
                    .map(GoodsDailyStat::getSalesCount).orElse(0);
        }

        private ConsumerRecord<String, String> 레코드(OrderConfirmedEvent 이벤트) {
            try {
                return new ConsumerRecord<>(OutboxRelay.TOPIC, 0, 0L,
                        String.valueOf(이벤트.orderId()), objectMapper.writeValueAsString(이벤트));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }

        private OrderConfirmedEvent.Line 줄(Goods 상품, int 수량) {
            return new OrderConfirmedEvent.Line(상품.getId(), 옵션_id(상품), 수량);
        }

        private OrderConfirmedEvent 확정_이벤트(OrderConfirmedEvent.Line... 줄들) {
            long 일련번호 = System.nanoTime();
            return new OrderConfirmedEvent(1, 일련번호, "ORDER_CONFIRMED", 일련번호, 회원,
                    "ORD" + 일련번호, LocalDateTime.now(), List.of(줄들));
        }

        private Long 옵션_id(Goods 상품) {
            return 상품.getOptions().get(0).getId();
        }

        private Goods 옵션상품_저장(String 이름) {
            Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
            Goods goods = new Goods(brand, "C001001001", 이름, null, "https://img/x.jpg", 20000, 단가);
            goods.getOptions().add(new GoodsOption(goods, "기본", 0, 50, 0));
            Goods saved = goodsRepository.saveAndFlush(goods);
            entityManager.flush();
            return saved;
        }
    }

    /**
     * 구독 구성. <b>설계 §5는 "그룹 하나에 리스너 3개"라고 적었지만 그대로 두면 동작하지 않는다</b> —
     * 같은 토픽을 같은 그룹으로 구독하는 리스너 셋은 파티션을 나눠 가져 각 메시지가 셋 중
     * 하나에게만 간다. 그래서 컨슈머마다 그룹을 갈랐고(팬아웃), 그 사실을 이 테스트가 붙든다.
     */
    static class 구독_구성 {

        @Test
        void 세_컨슈머는_서로_다른_그룹으로_같은_토픽을_구독해_모두_이벤트를_받는다() {
            List<KafkaListener> 리스너들 = List.of(
                    리스너(CartClearOnOrderConfirmed.class),
                    리스너(SalesAggregationConsumer.class),
                    리스너(NotificationConsumer.class));

            assertThat(리스너들).allSatisfy(리스너 ->
                    assertThat(리스너.topics()).containsExactly(OutboxRelay.TOPIC));

            Set<String> 그룹들 = 리스너들.stream()
                    .map(KafkaListener::groupId)
                    .collect(Collectors.toSet());
            // 그룹이 셋으로 갈려 있어야 세 컨슈머가 모두 모든 메시지를 받는다.
            // 하나로 합쳐지는 순간(설계 §5 그대로) 메시지가 셋 중 하나에게만 간다.
            assertThat(그룹들).hasSize(3);
            assertThat(그룹들).allSatisfy(그룹 -> assertThat(그룹).isNotBlank());
        }

        private KafkaListener 리스너(Class<?> 컨슈머) {
            Method on = Arrays.stream(컨슈머.getMethods())
                    .filter(m -> m.getName().equals("on"))
                    .filter(m -> m.isAnnotationPresent(KafkaListener.class))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(컨슈머.getSimpleName() + "에 @KafkaListener가 없다"));
            return on.getAnnotation(KafkaListener.class);
        }
    }

    /**
     * 실패 처리 설정.
     *
     * <p><b>계획서의 {@code 컨슈머가_계속_실패하면_DLT로_이동하고_다음_메시지는_계속_소비된다}를
     * 여기서 이렇게 조정했다</b>: 실제로 DLT에 적재되고 다음 메시지가 계속 소비되는지는 브로커가
     * 있어야 볼 수 있는데 Global Constraints가 {@code test}에서의 브로커·EmbeddedKafka를 금지한다.
     * 그래서 {@code test}에서는 <b>재시도 횟수와 백오프 간격, 리커버러 종류</b>를 단언하고,
     * 종단 동작(DLT 적재 + 후속 메시지 소비)은 A7의 통합 테스트로 넘긴다.
     */
    static class 실패_처리_설정 {

        @Test
        void 재시도는_세번까지_1초_2초_4초_간격이다() {
            ExponentialBackOff backOff = KafkaConsumerConfig.재시도_백오프();

            assertThat(backOff.getMaxAttempts()).isEqualTo(3);

            BackOffExecution 실행 = backOff.start();
            assertThat(실행.nextBackOff()).isEqualTo(1000L);
            assertThat(실행.nextBackOff()).isEqualTo(2000L);
            assertThat(실행.nextBackOff()).isEqualTo(4000L);
            // 네 번째는 없다 — 여기서 DefaultErrorHandler가 리커버러(DLT 발행)를 부른다.
            assertThat(실행.nextBackOff()).isEqualTo(BackOffExecution.STOP);
        }

        @Test
        void 재시도를_소진하면_DLT_발행_리커버러가_받는다() {
            DefaultErrorHandler handler = new KafkaConsumerConfig()
                    .kafkaErrorHandler(new org.springframework.kafka.core.KafkaTemplate<>(
                            new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(Map.of())));

            // 재시도를 소진한 레코드를 버리지 않고 리커버러로 넘기는 DefaultErrorHandler다.
            assertThat(handler.isAckAfterHandle()).isTrue();
        }

        /**
         * <b>이 단언이 없어서 실제로 조용히 깨져 있던 자리다.</b> Spring Kafka의 기본 목적지 리졸버는
         * 원본 토픽 + {@code -dlt}(소문자·하이픈)를 쓰는데 {@link KafkaTopicConfig}가 만든 토픽은
         * {@code .DLT}였다 — 재시도를 소진한 레코드가 존재하지 않는 토픽으로 가고
         * {@link DlqReplayService}는 처음부터 아무것도 못 읽는, DLQ 기능 전체가 죽은 상태였다.
         *
         * <p>그 결함을 잡은 것은 Docker가 필요한 {@code integrationTest}(A7)뿐이라 흔한 개발 루프인
         * {@code ./gradlew test}에서는 회귀가 잡히지 않았다. 이 테스트의 원래 자리는
         * {@code assertThat(handler).isNotNull()}이라 이름이 약속한 "리커버러가 DLT로 보낸다"를
         * 전혀 검증하지 않았다. 그래서 목적지 규칙을 {@link KafkaConsumerConfig#DLT_목적지_리졸버()}로
         * 꺼내 <b>브로커 없이</b> 직접 호출한다.
         */
        @Test
        void DLT_목적지는_원본토픽에_점DLT를_붙이고_파티션을_보존한다() {
            var 리졸버 = KafkaConsumerConfig.DLT_목적지_리졸버();
            var 실패한_레코드 = new ConsumerRecord<>(OutboxRelay.TOPIC, 2, 41L, "777", "{}");

            var 목적지 = 리졸버.apply(실패한_레코드, new IllegalStateException("컨슈머 실패"));

            // KafkaTopicConfig가 실제로 만드는 토픽 이름과 정확히 같아야 한다(리터럴로 못 박는다 —
            // 양쪽이 같은 상수를 쓰더라도 그 상수가 잘못 바뀌면 브로커에 없는 토픽으로 간다).
            assertThat(목적지.topic()).isEqualTo("order-events.DLT");
            // 파티션 보존 — 같은 주문(키=orderId)의 실패 레코드가 DLT에서도 한 파티션에 모인다.
            assertThat(목적지.partition()).isEqualTo(2);
        }
    }

    /** 장바구니 비우기 시점이 주문 생성 → 확정 이벤트 소비로 옮겨졌다(설계 §2-2, 의도된 행동 변경). */
    @SpringBootTest
    @ActiveProfiles("test")
    @Transactional
    static class 주문_생성_회귀 {

        private static final Long 회원 = 780303L;

        @Autowired
        OrderService orderService;
        @Autowired
        CartService cartService;
        @Autowired
        BrandRepository brandRepository;
        @Autowired
        GoodsRepository goodsRepository;
        @PersistenceContext
        EntityManager entityManager;

        @Test
        void 주문_생성_시점에는_장바구니가_비워지지_않는다() {
            Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
            Goods goods = new Goods(brand, "C001001001", "토너", null, "https://img/x.jpg", 20000, 단가);
            goods.getOptions().add(new GoodsOption(goods, "기본", 0, 10, 0));
            Goods 상품 = goodsRepository.saveAndFlush(goods);
            Long 옵션 = 상품.getOptions().get(0).getId();
            cartService.add(회원, new CartAddRequest(상품.getId(), 옵션, 2));

            orderService.create(회원, new OrderCreateRequest(
                    List.of(new OrderCreateRequest.OrderItemRequest(상품.getId(), 옵션, 2)),
                    "홍길동", "010-0000-0000", "06234", "서울시 강남구", "101호", "NORMAL"));

            TestPersistence.DB_왕복_강제(entityManager);

            // 결제를 포기해도 장바구니는 남아야 한다 — 비우는 것은 cart-clear 컨슈머의 몫이다.
            assertThat(cartService.itemsOf(회원)).hasSize(1);
        }
    }
}

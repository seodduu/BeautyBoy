package com.beautyboy.order;

import com.beautyboy.cart.CartService;
import com.beautyboy.cart.dto.CartAddRequest;
import com.beautyboy.cart.dto.CartItemResponse;
import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsOption;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.notification.Notification;
import com.beautyboy.notification.NotificationRepository;
import com.beautyboy.order.dto.OrderCreateRequest;
import com.beautyboy.outbox.OrderConfirmedEvent;
import com.beautyboy.outbox.OutboxEventRepository;
import com.beautyboy.payment.PaymentGateway;
import com.beautyboy.payment.PaymentRepository;
import com.beautyboy.payment.PaymentService;
import com.beautyboy.payment.dto.PaymentApproval;
import com.beautyboy.payment.dto.PaymentConfirmRequest;
import com.beautyboy.ranking.GoodsDailyStat;
import com.beautyboy.ranking.GoodsDailyStatRepository;
import com.beautyboy.ranking.RankingBatchService;
import com.beautyboy.ranking.WishStatProvider;
import com.beautyboy.support.TestPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 확정 후처리 3종(A4b — 동기 기준선).
 *
 * <p>이 태스크의 목적은 기능이 아니라 <b>측정</b>이다. 후처리를 결제 트랜잭션 안에서 직접 돌리면
 * 얼마나 느려지고 무엇이 함께 롤백되는지를 코드로 못 박아 두고, A5가 같은 기능을 이벤트 소비로
 * 옮겨 그 비용이 사라지는 것을 보인다. 그래서 {@code 후처리가_실패하면_결제도_롤백된다}는
 * 결함을 잡는 테스트가 아니라 <b>지금 구조의 대가를 기록하는</b> 테스트다.
 */
class PostOrderTasksTest {

    private static final int 단가 = 12000;

    /** 후처리 자체의 동작. 결제 경로를 태우지 않고 진입점을 직접 부른다. */
    @SpringBootTest
    @ActiveProfiles("test")
    @Transactional
    static class 후처리_동작 {

        private static final Long 회원 = 770101L;
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
        PostOrderTasks postOrderTasks;
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
        @PersistenceContext
        EntityManager entityManager;

        @Test
        void 확정되면_주문_상품만_장바구니에서_지워진다() {
            Goods 주문한_상품 = 옵션상품_저장("토너");
            Goods 안_주문한_상품 = 옵션상품_저장("클렌저");
            cartService.add(회원, new CartAddRequest(주문한_상품.getId(), 옵션_id(주문한_상품), 2));
            cartService.add(회원, new CartAddRequest(안_주문한_상품.getId(), 옵션_id(안_주문한_상품), 1));

            postOrderTasks.onOrderConfirmed(확정_이벤트(줄(주문한_상품, 2)));

            TestPersistence.DB_왕복_강제(entityManager);

            assertThat(cartService.itemsOf(회원))
                    .extracting(CartItemResponse::goodsNo)
                    .containsExactly(안_주문한_상품.getId());
        }

        @Test
        void 확정되면_판매집계가_수량만큼_는다() {
            Goods 상품 = 옵션상품_저장("세럼");

            postOrderTasks.onOrderConfirmed(확정_이벤트(줄(상품, 3)));
            postOrderTasks.onOrderConfirmed(확정_이벤트(줄(상품, 2)));

            TestPersistence.DB_왕복_강제(entityManager);

            // 덮어쓰기가 아니라 증분이다 — 두 주문이면 5여야 한다.
            assertThat(판매수량(상품)).isEqualTo(5);
        }

        @Test
        void 확정되면_알림이_한건_생긴다() {
            Goods 상품 = 옵션상품_저장("에센스");
            OrderConfirmedEvent 이벤트 = 확정_이벤트(줄(상품, 1));

            postOrderTasks.onOrderConfirmed(이벤트);

            TestPersistence.DB_왕복_강제(entityManager);

            List<Notification> 알림 = notificationRepository.findByMemberId(회원);
            assertThat(알림).hasSize(1);
            assertThat(알림.get(0).getType()).isEqualTo("ORDER_CONFIRMED");
            assertThat(알림.get(0).getMessage())
                    .isEqualTo("주문 " + 이벤트.orderNo() + " 결제가 완료됐어요.");
            assertThat(알림.get(0).getEventId()).isEqualTo(이벤트.eventId());
        }

        /**
         * 판매 이중 계상/소실 회귀. 증분 경로(이벤트)와 덮어쓰기 경로(배치)가 같은 컬럼을 두고
         * 겹치면, 배치가 도는 순간 컨슈머가 쌓아 둔 판매량이 조용히 사라진다.
         * 그래서 판매는 증분 하나만, 찜은 판매를 건드리지 않는 upsert로 갈랐다.
         */
        @Test
        void 랭킹_배치가_돌아도_증분된_판매량이_사라지지_않는다() {
            Goods 상품 = 옵션상품_저장("크림");
            찜_대상_상품_id.set(상품.getId());
            postOrderTasks.onOrderConfirmed(확정_이벤트(줄(상품, 4)));

            TestPersistence.DB_왕복_강제(entityManager);

            rankingBatchService.rebuild();

            TestPersistence.DB_왕복_강제(entityManager);

            GoodsDailyStat stat = goodsDailyStatRepository
                    .findById(new GoodsDailyStat.Key(상품.getId(), 오늘)).orElseThrow();
            assertThat(stat.getSalesCount()).isEqualTo(4);   // 배치가 0으로 덮어쓰면 여기서 걸린다
            assertThat(stat.getWishCount()).isEqualTo(5);    // 찜 수집은 그대로 살아 있어야 한다
        }

        private int 판매수량(Goods 상품) {
            return goodsDailyStatRepository.findById(new GoodsDailyStat.Key(상품.getId(), 오늘))
                    .map(GoodsDailyStat::getSalesCount).orElse(0);
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
     * 동기 방식의 대가.
     *
     * <p>클래스에 {@code @Transactional}을 걸지 않는다 — 확인하려는 것이 "확정 트랜잭션이
     * 실제로 롤백되는가"인데, 테스트가 바깥에서 트랜잭션을 감싸면 커밋·롤백 경계가 사라진다.
     */
    @SpringBootTest
    @ActiveProfiles("test")
    static class 후처리_실패의_대가 {

        private static final Long 회원 = 770202L;

        @TestConfiguration
        static class 터지는_후처리 {
            @Bean
            @Primary
            PostOrderTasks 터지는_후처리_구현() {
                return event -> {
                    throw new IllegalStateException("알림 서버 장애");
                };
            }

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
        OrderRepository orderRepository;
        @Autowired
        PaymentRepository paymentRepository;
        @Autowired
        OutboxEventRepository outboxEventRepository;
        @Autowired
        BrandRepository brandRepository;
        @Autowired
        GoodsRepository goodsRepository;

        private final List<Long> 만든_주문 = new ArrayList<>();
        private final List<Long> 만든_상품 = new ArrayList<>();
        private final List<Long> 만든_브랜드 = new ArrayList<>();

        @AfterEach
        void 뒷정리() {
            outboxEventRepository.deleteAll(outboxEventRepository.findAll().stream()
                    .filter(e -> 만든_주문.contains(e.getAggregateId())).toList());
            paymentRepository.deleteAll(paymentRepository.findAll().stream()
                    .filter(p -> 만든_주문.contains(p.getOrderId())).toList());
            만든_주문.forEach(orderRepository::deleteById);
            만든_상품.forEach(goodsRepository::deleteById);
            만든_브랜드.forEach(brandRepository::deleteById);
        }

        @Test
        void 후처리가_실패하면_결제도_롤백된다() {
            Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
            만든_브랜드.add(brand.getId());
            Goods goods = new Goods(brand, "C001001001", "토너", null, "https://img/x.jpg", 20000, 단가);
            goods.getOptions().add(new GoodsOption(goods, "기본", 0, 10, 0));
            Goods 저장된_상품 = goodsRepository.saveAndFlush(goods);
            만든_상품.add(저장된_상품.getId());

            Order order = new Order("ORD-" + System.nanoTime(), 회원, "홍길동", "010-0000-0000",
                    "06234", "서울시 강남구", "101호", "NORMAL", LocalDateTime.now());
            order.addItem(new OrderItem(저장된_상품.getId(), 저장된_상품.getOptions().get(0).getId(),
                    저장된_상품.getName(), "기본", 단가, 2));
            Order 저장된_주문 = orderRepository.saveAndFlush(order);
            만든_주문.add(저장된_주문.getId());

            assertThatThrownBy(() -> paymentService.confirm(회원,
                    new PaymentConfirmRequest(저장된_주문.getOrderNo(), "pk_post_fail", 단가 * 2)))
                    .isInstanceOf(IllegalStateException.class);

            // 후처리 하나가 터지면 결제까지 되돌아간다. 이것이 동기 방식의 대가이며 A5가 없애려는 성질이다.
            assertThat(orderRepository.findByOrderNo(저장된_주문.getOrderNo()).orElseThrow().getStatus())
                    .isEqualTo(Order.STATUS_PENDING);
            assertThat(paymentRepository.findAll().stream()
                    .filter(p -> 저장된_주문.getId().equals(p.getOrderId())).count()).isZero();
            assertThat(outboxEventRepository.findAll().stream()
                    .filter(e -> 저장된_주문.getId().equals(e.getAggregateId())).count()).isZero();
        }
    }

    /** 장바구니 비우기 시점이 주문 생성 → 결제 확정으로 옮겨졌다(설계 §2-2, 의도된 행동 변경). */
    @SpringBootTest
    @ActiveProfiles("test")
    @Transactional
    static class 주문_생성_회귀 {

        private static final Long 회원 = 770303L;

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

            // 결제를 포기해도 장바구니는 남아야 한다 — 비우는 것은 확정 후처리의 몫이다.
            assertThat(cartService.itemsOf(회원)).hasSize(1);
        }
    }
}

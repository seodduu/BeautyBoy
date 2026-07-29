package com.beautyboy.order;

import com.beautyboy.cart.CartService;
import com.beautyboy.cart.dto.CartAddRequest;
import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsOption;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.notification.NotificationRepository;
import com.beautyboy.outbox.OrderConfirmedEvent;
import com.beautyboy.outbox.OutboxEvent;
import com.beautyboy.outbox.OutboxEventRepository;
import com.beautyboy.ranking.GoodsDailyStat;
import com.beautyboy.ranking.GoodsDailyStatRepository;
import com.beautyboy.support.TestPersistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 확정 후처리 진입점(A5 — 아웃박스 발행 구현).
 *
 * <p>A4b에서는 이 진입점이 후처리 3종을 직접 실행했고, 이 파일도 그 동작(장바구니·집계·알림)과
 * 동기 방식의 대가(후처리 실패 → 결제 롤백)를 봤다. A5에서 <b>호출부는 그대로 둔 채</b> 구현이
 * 아웃박스 INSERT 하나로 바뀌었으므로, 그 검증들은 컨슈머 쪽으로 옮겨 갔다
 * ({@code outbox.PostOrderConsumersTest}).
 *
 * <p>여기 남는 것은 두 가지다 — <b>이벤트가 남는가</b>, 그리고 <b>confirm 트랜잭션 안에서는
 * 아무 후처리도 일어나지 않는가</b>(A4b 대비 달라진 성질이자 C2에서 응답시간이 줄어드는 이유).
 * 페이로드 단언은 실 MySQL의 JSON 정규화 때문에 문자열 {@code contains}가 아니라 역직렬화로 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostOrderTasksTest {

    private static final int 단가 = 12000;
    private static final Long 회원 = 770101L;
    private static final LocalDate 오늘 = LocalDate.now();

    @Autowired
    PostOrderTasks postOrderTasks;
    @Autowired
    OutboxEventRepository outboxEventRepository;
    @Autowired
    CartService cartService;
    @Autowired
    GoodsDailyStatRepository goodsDailyStatRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    ObjectMapper objectMapper;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void 확정되면_아웃박스에_PENDING_이벤트가_한건_남는다() throws Exception {
        Goods 상품 = 옵션상품_저장("토너");
        OrderConfirmedEvent 이벤트 = 확정_이벤트(줄(상품, 2));

        postOrderTasks.onOrderConfirmed(이벤트);

        TestPersistence.DB_왕복_강제(entityManager);

        List<OutboxEvent> 행들 = 주문_아웃박스(이벤트.orderId());
        assertThat(행들).hasSize(1);
        OutboxEvent 행 = 행들.get(0);
        assertThat(행.getStatus()).isEqualTo(OutboxEvent.STATUS_PENDING);
        assertThat(행.getEventType()).isEqualTo("ORDER_CONFIRMED");

        OrderConfirmedEvent 저장된 = objectMapper.readValue(행.getPayload(), OrderConfirmedEvent.class);
        assertThat(저장된.eventId()).isEqualTo(행.getId());
        assertThat(저장된.orderNo()).isEqualTo(이벤트.orderNo());
        assertThat(저장된.memberId()).isEqualTo(회원);
        assertThat(저장된.lines()).containsExactly(줄(상품, 2));
    }

    /**
     * A4b 대비 달라진 성질을 못 박는다. 후처리가 확정 트랜잭션 밖으로 나갔으므로 이 시점의
     * 장바구니·집계·알림은 전부 그대로여야 한다 — 하나라도 여기서 일어나면 동기 비용이
     * 남아 있다는 뜻이고, C2의 두 측정이 같은 것을 두 번 재게 된다.
     */
    @Test
    void 확정_트랜잭션_안에서는_후처리가_일어나지_않는다() {
        Goods 상품 = 옵션상품_저장("클렌저");
        cartService.add(회원, new CartAddRequest(상품.getId(), 옵션_id(상품), 2));

        postOrderTasks.onOrderConfirmed(확정_이벤트(줄(상품, 2)));

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(cartService.itemsOf(회원)).hasSize(1);                 // 아직 안 비워졌다
        assertThat(goodsDailyStatRepository.findById(
                new GoodsDailyStat.Key(상품.getId(), 오늘))).isEmpty();     // 집계도 안 늘었다
        assertThat(notificationRepository.findByMemberId(회원)).isEmpty();  // 알림도 없다
    }

    private List<OutboxEvent> 주문_아웃박스(Long orderId) {
        return outboxEventRepository.findAll().stream()
                .filter(e -> orderId.equals(e.getAggregateId()))
                .toList();
    }

    private OrderConfirmedEvent.Line 줄(Goods 상품, int 수량) {
        return new OrderConfirmedEvent.Line(상품.getId(), 옵션_id(상품), 수량);
    }

    private OrderConfirmedEvent 확정_이벤트(OrderConfirmedEvent.Line... 줄들) {
        long 일련번호 = System.nanoTime();
        return new OrderConfirmedEvent(1, null, "ORDER_CONFIRMED", 일련번호, 회원,
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

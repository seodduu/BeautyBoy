package com.beautyboy.order;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.outbox.OutboxEvent;
import com.beautyboy.outbox.OutboxEventRepository;
import com.beautyboy.support.TestPersistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 취소의 주문 쪽 절반(OrderCancelPort 구현)의 계약 검증.
 *
 * <p>클래스 {@code @Transactional}이 호출자의 트랜잭션 역할을 한다 — 구현이 요구하는
 * {@code Propagation.MANDATORY}를 충족시킨다({@code OrderConfirmServiceTest}와 같은 관례).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderCancelServiceTest {

    private static final Long 회원 = 1L;
    private static final Long 다른회원 = 999L;

    /** 픽스처 단가 — 계획서의 스냅샷 단가 검증값. */
    private static final int 토너_단가 = 24_100;
    private static final int 크림_단가 = 31_000;

    @Autowired
    OrderCancelPort orderCancelPort;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderCancelRepository orderCancelRepository;
    @Autowired
    OutboxEventRepository outboxEventRepository;
    @Autowired
    ObjectMapper objectMapper;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("부분 취소 — 잔여 수량과 환불액이 스냅샷 단가로 계산된다")
    void 부분취소_잔여수량과_환불액이_스냅샷단가로_계산된다() {
        Order order = 결제완료_주문_저장();
        OrderItem 토너 = 항목(order, "토너");

        OrderCancelPort.CancelApplication app = orderCancelPort.applyCancel(
                order.getOrderNo(), 회원,
                List.of(new OrderCancelPort.CancelLine(토너.getId(), 1)), "단순 변심");

        assertThat(app.refundAmount()).isEqualTo(토너_단가);
        assertThat(app.statusAfter()).isEqualTo(Order.STATUS_PARTIALLY_CANCELED);
        assertThat(app.orderId()).isEqualTo(order.getId());
        assertThat(app.orderNo()).isEqualTo(order.getOrderNo());

        TestPersistence.DB_왕복_강제(entityManager);

        Order 다시_읽은_주문 = orderRepository.findByOrderNo(order.getOrderNo()).orElseThrow();
        assertThat(다시_읽은_주문.getStatus()).isEqualTo(Order.STATUS_PARTIALLY_CANCELED);
        assertThat(항목(다시_읽은_주문, "토너").getCanceledQuantity()).isEqualTo(1);
        assertThat(항목(다시_읽은_주문, "토너").remainingQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("전 항목 전량 취소면 CANCELED다")
    void 전항목_전량취소면_CANCELED다() {
        Order order = 결제완료_주문_저장();

        OrderCancelPort.CancelApplication app = orderCancelPort.applyCancel(
                order.getOrderNo(), 회원, 전량_취소줄(order), "주문 실수");

        assertThat(app.statusAfter()).isEqualTo(Order.STATUS_CANCELED);
        // 토너 24_100 × 2 + 크림 31_000 × 1 + 사은품 0 × 1
        assertThat(app.refundAmount()).isEqualTo(토너_단가 * 2 + 크림_단가);
    }

    @Test
    @DisplayName("옵션 없는 항목은 stockLines에서 빠진다 — 재고 비관리")
    void 옵션없는_항목은_stockLines에서_빠진다() {
        Order order = 결제완료_주문_저장();

        OrderCancelPort.CancelApplication app = orderCancelPort.applyCancel(
                order.getOrderNo(), 회원, 전량_취소줄(order), "단순 변심");

        // 주문 줄은 3개(토너·크림·사은품)지만 재고 복원 대상은 옵션 있는 2개뿐이다.
        assertThat(app.stockLines()).hasSize(2);
        assertThat(app.stockLines()).allSatisfy(line -> assertThat(line.optionId()).isNotNull());
    }

    @Test
    @DisplayName("잔여 수량을 넘으면 ORDER_CANCEL_QUANTITY_EXCEEDED")
    void 잔여수량_초과는_ORDER_CANCEL_QUANTITY_EXCEEDED() {
        Order order = 결제완료_주문_저장();
        OrderItem 토너 = 항목(order, "토너");   // 수량 2

        assertThatThrownBy(() -> orderCancelPort.applyCancel(order.getOrderNo(), 회원,
                List.of(new OrderCancelPort.CancelLine(토너.getId(), 3)), "단순 변심"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_CANCEL_QUANTITY_EXCEEDED);
    }

    @Test
    @DisplayName("같은 항목을 두 줄로 나눠 잔여를 넘겨도 걸러진다 — 줄 단위 검증의 구멍 방어")
    void 같은항목_두줄로_잔여초과해도_걸러진다() {
        Order order = 결제완료_주문_저장();
        OrderItem 토너 = 항목(order, "토너");   // 수량 2

        // 각 줄만 보면 잔여 이내지만 합이 3이라 초과다. 누적 반영이라 두 번째 줄에서 걸린다.
        assertThatThrownBy(() -> orderCancelPort.applyCancel(order.getOrderNo(), 회원,
                List.of(new OrderCancelPort.CancelLine(토너.getId(), 1),
                        new OrderCancelPort.CancelLine(토너.getId(), 2)), "단순 변심"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_CANCEL_QUANTITY_EXCEEDED);
    }

    @Test
    @DisplayName("PENDING 주문은 ORDER_INVALID_STATUS")
    void PENDING주문은_ORDER_INVALID_STATUS() {
        Order order = 결제대기_주문_저장();
        OrderItem 토너 = 항목(order, "토너");

        assertThatThrownBy(() -> orderCancelPort.applyCancel(order.getOrderNo(), 회원,
                List.of(new OrderCancelPort.CancelLine(토너.getId(), 1)), "단순 변심"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
    }

    @Test
    @DisplayName("남의 주문은 존재를 숨긴다 — ORDER_NOT_FOUND")
    void 남의주문은_ORDER_NOT_FOUND() {
        Order order = 결제완료_주문_저장();
        OrderItem 토너 = 항목(order, "토너");

        assertThatThrownBy(() -> orderCancelPort.applyCancel(order.getOrderNo(), 다른회원,
                List.of(new OrderCancelPort.CancelLine(토너.getId(), 1)), "단순 변심"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 주문 항목 ID를 섞으면 ORDER_NOT_FOUND — 이 주문 소속이 아니다")
    void 남의_주문항목ID를_섞으면_ORDER_NOT_FOUND() {
        Order 내_주문 = 결제완료_주문_저장();
        Order 남의_주문 = 결제완료_주문_저장();
        OrderItem 남의_토너 = 항목(남의_주문, "토너");

        assertThatThrownBy(() -> orderCancelPort.applyCancel(내_주문.getOrderNo(), 회원,
                List.of(new OrderCancelPort.CancelLine(남의_토너.getId(), 1)), "단순 변심"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("빈 목록은 ORDER_CANCEL_EMPTY — 주문을 읽기도 전에 거른다")
    void 빈_목록은_ORDER_CANCEL_EMPTY() {
        Order order = 결제완료_주문_저장();

        assertThatThrownBy(() -> orderCancelPort.applyCancel(order.getOrderNo(), 회원,
                List.of(), "단순 변심"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_CANCEL_EMPTY);
    }

    @Test
    @DisplayName("취소 이력이 회차로 저장된다 — 2회 취소 → order_cancel 2행")
    void 취소이력이_회차로_저장된다() {
        Order order = 결제완료_주문_저장();
        OrderItem 토너 = 항목(order, "토너");   // 수량 2

        orderCancelPort.applyCancel(order.getOrderNo(), 회원,
                List.of(new OrderCancelPort.CancelLine(토너.getId(), 1)), "단순 변심");
        orderCancelPort.applyCancel(order.getOrderNo(), 회원,
                List.of(new OrderCancelPort.CancelLine(토너.getId(), 1)), "주문 실수");

        TestPersistence.DB_왕복_강제(entityManager);

        List<OrderCancel> 이력 = orderCancelRepository.findByOrderIdOrderByIdAsc(order.getId());
        assertThat(이력).hasSize(2);
        assertThat(이력).extracting(OrderCancel::getReason)
                .containsExactly("단순 변심", "주문 실수");
        assertThat(이력).allSatisfy(회차 -> {
            assertThat(회차.getRefundAmount()).isEqualTo(토너_단가);
            assertThat(회차.getItems()).hasSize(1);
            assertThat(회차.getItems().get(0).getOrderItemId()).isEqualTo(토너.getId());
            assertThat(회차.getItems().get(0).getQuantity()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("아웃박스에 ORDER_CANCELED가 실린다 — 같은 트랜잭션이라 유령 이벤트가 없다")
    void 아웃박스에_ORDER_CANCELED가_실린다() throws Exception {
        Order order = 결제완료_주문_저장();
        OrderItem 토너 = 항목(order, "토너");

        orderCancelPort.applyCancel(order.getOrderNo(), 회원,
                List.of(new OrderCancelPort.CancelLine(토너.getId(), 1)), "단순 변심");

        TestPersistence.DB_왕복_강제(entityManager);

        List<OutboxEvent> 행들 = outboxEventRepository.findAll();
        assertThat(행들).hasSize(1);
        OutboxEvent 행 = 행들.get(0);
        assertThat(행.getEventType()).isEqualTo("ORDER_CANCELED");

        JsonNode payload = objectMapper.readTree(행.getPayload());
        assertThat(payload.path("eventType").asText()).isEqualTo("ORDER_CANCELED");
        assertThat(payload.path("eventId").asLong()).isEqualTo(행.getId());   // 행 PK와 같아야 멱등 키가 된다
        assertThat(payload.path("version").asInt()).isEqualTo(1);
        assertThat(payload.path("orderNo").asText()).isEqualTo(order.getOrderNo());
        assertThat(payload.path("memberId").asLong()).isEqualTo(회원);
        assertThat(payload.path("refundAmount").asInt()).isEqualTo(토너_단가);
        assertThat(payload.path("canceledAt").isTextual()).isTrue();

        JsonNode lines = payload.path("lines");
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).path("goodsId").asLong()).isEqualTo(토너.getGoodsId());
        assertThat(lines.get(0).path("optionId").asLong()).isEqualTo(토너.getOptionId());
        assertThat(lines.get(0).path("quantity").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("취소된 주문도 잔여가 있으면 다시 취소할 수 있다 — PARTIALLY_CANCELED 통과")
    void 부분취소된_주문은_다시_취소할_수_있다() {
        Order order = 결제완료_주문_저장();
        OrderItem 토너 = 항목(order, "토너");

        orderCancelPort.applyCancel(order.getOrderNo(), 회원,
                List.of(new OrderCancelPort.CancelLine(토너.getId(), 1)), "단순 변심");
        OrderCancelPort.CancelApplication 두번째 = orderCancelPort.applyCancel(
                order.getOrderNo(), 회원,
                List.of(new OrderCancelPort.CancelLine(토너.getId(), 1)), "단순 변심");

        assertThat(두번째.statusAfter()).isEqualTo(Order.STATUS_PARTIALLY_CANCELED);   // 크림·사은품이 남아 있다
    }

    /** 옵션 있는 줄 2개(토너 수량 2 · 크림 수량 1) + 옵션 없는 줄 1개(사은품). */
    private Order 결제대기_주문_저장() {
        Order order = new Order("ORD-" + System.nanoTime(), 회원, "홍길동", "010-0000-0000",
                "06234", "서울시 강남구", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(1L, 10L, "토너", "200ml", 토너_단가, 2));
        order.addItem(new OrderItem(2L, 20L, "크림", "50ml", 크림_단가, 1));
        order.addItem(new OrderItem(3L, null, "사은품 파우치", null, 0, 1));
        return orderRepository.saveAndFlush(order);
    }

    private Order 결제완료_주문_저장() {
        Order order = 결제대기_주문_저장();
        order.markPaid(LocalDateTime.now());
        return orderRepository.saveAndFlush(order);
    }

    private OrderItem 항목(Order order, String goodsName) {
        return order.getItems().stream()
                .filter(i -> goodsName.equals(i.getGoodsName()))
                .findFirst()
                .orElseThrow();
    }

    private List<OrderCancelPort.CancelLine> 전량_취소줄(Order order) {
        return order.getItems().stream()
                .map(i -> new OrderCancelPort.CancelLine(i.getId(), i.getQuantity()))
                .toList();
    }
}

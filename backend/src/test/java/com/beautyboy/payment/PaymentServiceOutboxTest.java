package com.beautyboy.payment;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsOption;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.order.Order;
import com.beautyboy.order.OrderItem;
import com.beautyboy.order.OrderRepository;
import com.beautyboy.outbox.OrderConfirmedEvent;
import com.beautyboy.outbox.OutboxEvent;
import com.beautyboy.outbox.OutboxEventRepository;
import com.beautyboy.payment.dto.PaymentApproval;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 확정 트랜잭션의 아웃박스 발행 지점(Task A3).
 *
 * <p><b>클래스 {@code @Transactional}을 걸지 않는다</b> — {@link PaymentStockConfirmTest}와 같은 이유다.
 * 확인하려는 것이 "확정 커밋과 아웃박스 INSERT가 같은 트랜잭션이라 함께 커밋되고 함께 롤백된다"인데,
 * 테스트가 바깥에서 트랜잭션을 감싸면 승인 호출이 그 트랜잭션에 참여해 커밋·롤백 경계 자체가 사라진다.
 * 대신 실제로 커밋되므로 {@link #뒷정리()}에서 직접 지운다.
 *
 * <p>페이로드 단언은 문자열 {@code contains}가 아니라 <b>역직렬화</b>로 한다. 실 MySQL은 JSON 컬럼을
 * 정규화해 저장하므로({@code "eventId": 1}처럼 콜론 뒤 공백) 공백 없는 문자열을 가정한 단언은
 * H2에서만 녹색인 거짓 신호가 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentServiceOutboxTest {

    private static final Long 회원 = 9201L;
    private static final int 단가 = 16000;

    @TestConfiguration
    static class 가짜_게이트웨이_설정 {
        static int approvedAmount;

        @Bean
        @Primary
        PaymentGateway fakeGateway() {
            return new PaymentGateway() {
                @Override
                public PaymentApproval confirm(String paymentKey, String orderNo, int amount) {
                    return new PaymentApproval(paymentKey, approvedAmount, "DONE", "{\"raw\":true}");
                }

                @Override
                public void cancel(String paymentKey, String reason) {
                }

                @Override
                public void cancelPartial(String paymentKey, String reason, int cancelAmount) {
                }
            };
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
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
                .filter(e -> 만든_주문.contains(e.getAggregateId()))
                .toList());
        paymentRepository.deleteAll(paymentRepository.findAll().stream()
                .filter(p -> 만든_주문.contains(p.getOrderId()))
                .toList());
        만든_주문.forEach(orderRepository::deleteById);
        만든_상품.forEach(goodsRepository::deleteById);
        만든_브랜드.forEach(brandRepository::deleteById);
    }

    @Test
    void 확정_성공시_같은_트랜잭션에서_아웃박스가_남는다() throws Exception {
        가짜_게이트웨이_설정.approvedAmount = 단가 * 4;
        GoodsOption 옵션 = 옵션_저장("토너", 10);
        Goods 사은품 = 상품_저장("사은품 파우치");
        Order 주문 = 주문_저장(항목(옵션, 3),
                new OrderItem(사은품.getId(), null, 사은품.getName(), null, 단가, 1));

        승인요청(주문.getOrderNo(), "pk_outbox", 단가 * 4)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        // 확정과 아웃박스가 함께 커밋됐다 — 주문 PAID, payment 1건, 아웃박스 1건.
        assertThat(orderRepository.findByOrderNo(주문.getOrderNo()).orElseThrow().getStatus())
                .isEqualTo(Order.STATUS_PAID);
        assertThat(주문_결제건수(주문)).isEqualTo(1);

        List<OutboxEvent> 아웃박스 = 주문_아웃박스(주문);
        assertThat(아웃박스).hasSize(1);
        OutboxEvent 행 = 아웃박스.get(0);
        assertThat(행.getStatus()).isEqualTo(OutboxEvent.STATUS_PENDING);
        assertThat(행.getAggregateType()).isEqualTo("ORDER");
        assertThat(행.getAggregateId()).isEqualTo(주문.getId());
        assertThat(행.getEventType()).isEqualTo("ORDER_CONFIRMED");

        OrderConfirmedEvent 이벤트 = objectMapper.readValue(행.getPayload(), OrderConfirmedEvent.class);
        assertThat(이벤트.version()).isEqualTo(1);
        assertThat(이벤트.eventId()).isEqualTo(행.getId());
        assertThat(이벤트.eventType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(이벤트.orderId()).isEqualTo(주문.getId());
        assertThat(이벤트.memberId()).isEqualTo(회원);
        assertThat(이벤트.orderNo()).isEqualTo(주문.getOrderNo());
        assertThat(이벤트.confirmedAt()).isNotNull();

        // eventLines는 stockLines와 달리 optionId 없는 줄도 담는다 —
        // 장바구니 비우기·알림 컨슈머(A5)는 재고 비관리 상품도 처리해야 하기 때문이다.
        assertThat(이벤트.lines()).containsExactlyInAnyOrder(
                new OrderConfirmedEvent.Line(옵션.getGoods().getId(), 옵션.getId(), 3),
                new OrderConfirmedEvent.Line(사은품.getId(), null, 1));
    }

    @Test
    void 금액_불일치로_실패하면_아웃박스도_남지_않는다() throws Exception {
        가짜_게이트웨이_설정.approvedAmount = 10;      // 토스가 알려온 승인액(조작됨)
        GoodsOption 옵션 = 옵션_저장("클렌저", 10);
        Order 주문 = 주문_저장(항목(옵션, 3));          // 우리가 계산한 진짜 금액은 48000

        승인요청(주문.getOrderNo(), "pk_tampered", 10)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));

        // 유령 이벤트가 없다 — 아웃박스 INSERT가 토스 호출·금액 대조보다 뒤이자 같은 트랜잭션이라,
        // 실패는 결제와 이벤트를 함께 되돌린다.
        assertThat(주문_아웃박스(주문)).isEmpty();
        assertThat(orderRepository.findByOrderNo(주문.getOrderNo()).orElseThrow().getStatus())
                .isEqualTo(Order.STATUS_PENDING);
        assertThat(주문_결제건수(주문)).isZero();
    }

    private List<OutboxEvent> 주문_아웃박스(Order 주문) {
        return outboxEventRepository.findAll().stream()
                .filter(e -> 주문.getId().equals(e.getAggregateId()))
                .toList();
    }

    private long 주문_결제건수(Order 주문) {
        return paymentRepository.findAll().stream()
                .filter(p -> 주문.getId().equals(p.getOrderId()))
                .count();
    }

    private ResultActions 승인요청(String orderNo, String paymentKey, int amount) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "orderNo", orderNo, "paymentKey", paymentKey, "amount", amount));
        return mockMvc.perform(post("/api/v1/payments/confirm")
                .with(authentication(new UsernamePasswordAuthenticationToken(
                        회원, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private OrderItem 항목(GoodsOption 옵션, int 수량) {
        return new OrderItem(옵션.getGoods().getId(), 옵션.getId(),
                옵션.getGoods().getName(), 옵션.getName(), 단가, 수량);
    }

    private Order 주문_저장(OrderItem... 항목들) {
        Order order = new Order("ORD-" + System.nanoTime(), 회원, "홍길동", "010-0000-0000",
                "06234", "서울시 강남구", "101호", "NORMAL", LocalDateTime.now());
        for (OrderItem 항목 : 항목들) {
            order.addItem(항목);
        }
        Order saved = orderRepository.saveAndFlush(order);
        만든_주문.add(saved.getId());
        return saved;
    }

    private Goods 상품_저장(String 이름) {
        return 저장한다(이름, null);
    }

    private GoodsOption 옵션_저장(String 이름, int 재고) {
        return 저장한다(이름, 재고).getOptions().get(0);
    }

    /** 브랜드·상품(+옵션 0~1개)을 한 번에 커밋한다. 재고가 null이면 옵션 없는 상품이다. */
    private Goods 저장한다(String 이름, Integer 재고) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        만든_브랜드.add(brand.getId());
        Goods goods = new Goods(brand, "C001001001", 이름, null, "https://img/x.jpg", 20000, 단가);
        if (재고 != null) {
            goods.getOptions().add(new GoodsOption(goods, "기본", 0, 재고, 0));
        }
        Goods saved = goodsRepository.saveAndFlush(goods);
        만든_상품.add(saved.getId());
        return saved;
    }
}

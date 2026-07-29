package com.beautyboy.order;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsOption;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.payment.FakeCancelGateway;
import com.beautyboy.payment.Payment;
import com.beautyboy.payment.PaymentCompensationRepository;
import com.beautyboy.payment.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 취소 API의 응답 계약 검증(설계 §9-1·§9-2). 여기서 단언하는 JSON 필드가 곧 프론트와의 약속이다.
 *
 * <p>클래스 {@code @Transactional}이 없는 이유는 취소가 REQUIRES_NEW 보상 행을 남기기 때문이다 —
 * 롤백 경계를 흐리지 않도록 각 테스트가 직접 정리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// 전용 H2 데이터베이스를 쓴다 — 이 클래스는 트랜잭션 없이 실제로 커밋하기 때문이다. 공용
// jdbc:h2:mem:beautyboy를 쓰면 (1) 커밋된 픽스처가 다른 테스트로 새고, (2) create-drop이라
// 이 컨텍스트가 닫힐 때 아직 살아 있는 다른 컨텍스트의 테이블까지 지워 "Table not found"가 난다.
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:cancelapi;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
class OrderCancelApiTest {

    private static final Long 회원 = 1L;
    private static final int 토너_단가 = 24_100;

    @TestConfiguration
    static class 가짜_게이트웨이_설정 {
        @Bean
        @Primary
        FakeCancelGateway fakeCancelGateway() {
            return new FakeCancelGateway();
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    FakeCancelGateway gateway;
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
    BrandRepository brandRepository;

    @BeforeEach
    void 초기화() {
        gateway.reset();
        compensationRepository.deleteAll();
        paymentRepository.deleteAll();
        orderCancelRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("취소 성공 — 설계 §9-1 응답 필드가 전부 실린다")
    void 취소_성공_응답계약() throws Exception {
        주문 주문 = 결제완료_주문(2);

        취소요청(주문.orderNo(), Map.of("items",
                List.of(Map.of("orderItemId", 주문.orderItemId(), "quantity", 1)),
                "reason", "단순 변심"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(주문.orderNo()))
                .andExpect(jsonPath("$.data.status").value(Order.STATUS_PARTIALLY_CANCELED))
                .andExpect(jsonPath("$.data.refundAmount").value(토너_단가))
                .andExpect(jsonPath("$.data.canceledAt").exists());
    }

    @Test
    @DisplayName("전량 취소하면 status가 CANCELED다")
    void 전량_취소하면_CANCELED다() throws Exception {
        주문 주문 = 결제완료_주문(2);

        취소요청(주문.orderNo(), Map.of("items",
                List.of(Map.of("orderItemId", 주문.orderItemId(), "quantity", 2)),
                "reason", "주문 실수"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(Order.STATUS_CANCELED))
                .andExpect(jsonPath("$.data.refundAmount").value(토너_단가 * 2));
    }

    @Test
    @DisplayName("비로그인은 401")
    void 비로그인은_401() throws Exception {
        mockMvc.perform(post("/api/v1/orders/ORD-ANY/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("빈 items는 400 — 요청 검증에서 걸린다")
    void 빈_items는_400() throws Exception {
        주문 주문 = 결제완료_주문(2);

        취소요청(주문.orderNo(), Map.of("items", List.of(), "reason", "단순 변심"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PENDING 주문은 409 ORDER_INVALID_STATUS")
    void PENDING주문은_409() throws Exception {
        주문 주문 = 결제대기_주문(2);

        취소요청(주문.orderNo(), Map.of("items",
                List.of(Map.of("orderItemId", 주문.orderItemId(), "quantity", 1)),
                "reason", "단순 변심"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_STATUS"));
    }

    @Test
    @DisplayName("잔여 초과는 409 ORDER_CANCEL_QUANTITY_EXCEEDED")
    void 잔여초과는_409() throws Exception {
        주문 주문 = 결제완료_주문(2);

        취소요청(주문.orderNo(), Map.of("items",
                List.of(Map.of("orderItemId", 주문.orderItemId(), "quantity", 3)),
                "reason", "단순 변심"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CANCEL_QUANTITY_EXCEEDED"));
    }

    @Test
    @DisplayName("사유가 200자를 넘으면 400")
    void 사유가_200자를_넘으면_400() throws Exception {
        주문 주문 = 결제완료_주문(2);

        취소요청(주문.orderNo(), Map.of("items",
                List.of(Map.of("orderItemId", 주문.orderItemId(), "quantity", 1)),
                "reason", "가".repeat(201)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("토스 실패는 502 PAYMENT_CANCEL_FAILED — 롤백됐으므로 재시도가 안전하다")
    void 토스_실패는_502() throws Exception {
        주문 주문 = 결제완료_주문(2);
        gateway.failNext(new com.beautyboy.payment.PaymentGatewayException(
                "400 BAD_REQUEST", null, true, "INVALID_REQUEST"));

        취소요청(주문.orderNo(), Map.of("items",
                List.of(Map.of("orderItemId", 주문.orderItemId(), "quantity", 1)),
                "reason", "단순 변심"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_FAILED"));
    }

    @Test
    @DisplayName("목록에 status가 실린다")
    void 목록에_status가_실린다() throws Exception {
        주문 주문 = 결제완료_주문(2);
        취소한다(주문, 1);

        mockMvc.perform(get("/api/v1/orders").with(로그인(회원)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderNo").value(주문.orderNo()))
                .andExpect(jsonPath("$.data.content[0].status")
                        .value(Order.STATUS_PARTIALLY_CANCELED));
    }

    @Test
    @DisplayName("상세에 canceledQuantity·refundedAmount·cancels가 실린다")
    void 상세에_취소정보가_실린다() throws Exception {
        주문 주문 = 결제완료_주문(2);
        취소한다(주문, 1);

        mockMvc.perform(get("/api/v1/orders/" + 주문.orderNo()).with(로그인(회원)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(Order.STATUS_PARTIALLY_CANCELED))
                .andExpect(jsonPath("$.data.refundedAmount").value(토너_단가))
                .andExpect(jsonPath("$.data.items[0].orderItemId").value(주문.orderItemId()))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].canceledQuantity").value(1))
                .andExpect(jsonPath("$.data.cancels.length()").value(1))
                .andExpect(jsonPath("$.data.cancels[0].refundAmount").value(토너_단가))
                .andExpect(jsonPath("$.data.cancels[0].reason").value("단순 변심"))
                .andExpect(jsonPath("$.data.cancels[0].canceledAt").exists());
    }

    @Test
    @DisplayName("취소가 없는 주문은 refundedAmount 0에 빈 cancels다")
    void 취소가_없으면_빈_이력이다() throws Exception {
        주문 주문 = 결제완료_주문(2);

        mockMvc.perform(get("/api/v1/orders/" + 주문.orderNo()).with(로그인(회원)))
                .andExpect(jsonPath("$.data.refundedAmount").value(0))
                .andExpect(jsonPath("$.data.cancels.length()").value(0))
                .andExpect(jsonPath("$.data.items[0].canceledQuantity").value(0));
    }

    private void 취소한다(주문 주문, int quantity) throws Exception {
        취소요청(주문.orderNo(), Map.of("items",
                List.of(Map.of("orderItemId", 주문.orderItemId(), "quantity", quantity)),
                "reason", "단순 변심"))
                .andExpect(status().isOk());
    }

    private ResultActions 취소요청(String orderNo, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/orders/" + orderNo + "/cancel")
                .with(로그인(회원))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private RequestPostProcessor 로그인(Long memberId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                memberId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private record 주문(String orderNo, Long orderItemId) {
    }

    private 주문 결제대기_주문(int quantity) {
        Brand brand = brandRepository.saveAndFlush(new Brand("브랜드" + System.nanoTime(), null));
        Goods goods = new Goods(brand, "C001001001", "토너", null, "https://img/x.jpg",
                30_000, 토너_단가);
        goods.getOptions().add(new GoodsOption(goods, "200ml", 0, 100, 0));
        Goods 저장된_상품 = goodsRepository.saveAndFlush(goods);
        GoodsOption option = 저장된_상품.getOptions().get(0);

        Order order = new Order("ORD-" + System.nanoTime(), 회원, "홍길동", "010-0000-0000",
                "06234", "서울시 강남구", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(저장된_상품.getId(), option.getId(), "토너", "200ml",
                토너_단가, quantity));
        Order saved = orderRepository.saveAndFlush(order);
        return new 주문(saved.getOrderNo(), saved.getItems().get(0).getId());
    }

    private 주문 결제완료_주문(int quantity) {
        주문 만들어진_주문 = 결제대기_주문(quantity);
        Order order = orderRepository.findByOrderNo(만들어진_주문.orderNo()).orElseThrow();
        order.markPaid(LocalDateTime.now());
        Order saved = orderRepository.saveAndFlush(order);
        paymentRepository.saveAndFlush(new Payment(saved.getId(), "pk_" + saved.getOrderNo(),
                saved.getPayableAmount(), "{\"raw\":true}", LocalDateTime.now()));
        return 만들어진_주문;
    }
}

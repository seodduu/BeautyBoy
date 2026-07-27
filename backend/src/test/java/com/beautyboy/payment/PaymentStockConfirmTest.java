package com.beautyboy.payment;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsOption;
import com.beautyboy.catalog.GoodsOptionRepository;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.order.Order;
import com.beautyboy.order.OrderItem;
import com.beautyboy.order.OrderRepository;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 승인 트랜잭션의 재고 차감 사양(계획서 §2·§3-4).
 *
 * <p><b>클래스 {@code @Transactional}을 걸지 않는다.</b> 이 테스트가 확인하려는 것이
 * "서비스 트랜잭션이 롤백되면 차감도 되돌아간다"인데, 테스트가 바깥에서 트랜잭션을 감싸면
 * 서비스 호출이 그 트랜잭션에 참여해 롤백 경계 자체가 사라진다 — 차감도 복원도 관찰되지 않는다.
 * 대신 만든 데이터가 실제로 커밋되므로 {@link #뒷정리()}에서 직접 지운다
 * ({@code AuthRefreshConcurrencyScenario}가 같은 이유로 같은 구조다).
 *
 * <p>재조회는 리포지토리로 한다. 테스트 메서드가 트랜잭션이 아니라 리포지토리 호출마다
 * 새 트랜잭션·새 영속성 컨텍스트가 열리므로, 1차 캐시가 아니라 항상 DB의 현재 값을 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentStockConfirmTest {

    private static final Long 회원 = 9101L;
    private static final int 단가 = 16000;

    /**
     * 가짜 게이트웨이. {@code PaymentConfirmTest}의 것에 두 가지를 더했다 —
     * <b>confirm 호출 기록</b>(품절이면 토스를 아예 부르지 않았음을 증명하려면 호출 여부가 필요하다)과
     * <b>예외 주입 스위치</b>(토스 통신 실패 시 차감이 롤백되는지 보려면 실패를 만들어야 한다).
     */
    @TestConfiguration
    static class 가짜_게이트웨이_설정 {
        static int approvedAmount;
        static boolean confirm이_터진다;
        static final List<String> confirm호출 = new CopyOnWriteArrayList<>();
        static final List<String> cancel호출 = new CopyOnWriteArrayList<>();

        static void 초기화(int 승인액) {
            approvedAmount = 승인액;
            confirm이_터진다 = false;
            confirm호출.clear();
            cancel호출.clear();
        }

        @Bean
        @Primary
        PaymentGateway fakeGateway() {
            return new PaymentGateway() {
                @Override
                public PaymentApproval confirm(String paymentKey, String orderNo, int amount) {
                    confirm호출.add(paymentKey);
                    if (confirm이_터진다) {
                        throw new PaymentGatewayException("토스 통신 실패(주입)");
                    }
                    return new PaymentApproval(paymentKey, approvedAmount, "DONE", "{\"raw\":true}");
                }

                @Override
                public void cancel(String paymentKey, String reason) {
                    cancel호출.add(paymentKey);
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
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    GoodsOptionRepository goodsOptionRepository;

    private final List<Long> 만든_주문 = new ArrayList<>();
    private final List<Long> 만든_상품 = new ArrayList<>();
    private final List<Long> 만든_브랜드 = new ArrayList<>();

    @AfterEach
    void 뒷정리() {
        List<Payment> 남은_결제 = paymentRepository.findAll().stream()
                .filter(p -> 만든_주문.contains(p.getOrderId()))
                .toList();
        paymentRepository.deleteAll(남은_결제);
        만든_주문.forEach(orderRepository::deleteById);
        만든_상품.forEach(goodsRepository::deleteById);
        만든_브랜드.forEach(brandRepository::deleteById);
    }

    @Test
    void 승인하면_재고가_수량만큼_줄어든다() throws Exception {
        가짜_게이트웨이_설정.초기화(단가 * 3);
        GoodsOption 옵션 = 옵션_저장("토너", 10);
        Order 주문 = 주문_저장(항목(옵션, 3));

        승인요청(주문.getOrderNo(), "pk_ok", 단가 * 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        assertThat(재조회_재고(옵션)).isEqualTo(7);
        assertThat(재조회_상태(주문)).isEqualTo(Order.STATUS_PAID);
    }

    @Test
    void 품절이면_토스를_부르지_않고_409_ORDER_OUT_OF_STOCK이다() throws Exception {
        // 차감이 토스 호출보다 앞이라는 순서를 못 박는다. 뒤에 있으면 돈이 먼저 움직이고
        // 우리가 승인을 취소해야 한다 — 그 보상 호출을 없애려고 순서를 이렇게 정했다(§2 결정 4).
        가짜_게이트웨이_설정.초기화(단가 * 2);
        GoodsOption 옵션 = 옵션_저장("세럼", 1);
        Order 주문 = 주문_저장(항목(옵션, 2));

        승인요청(주문.getOrderNo(), "pk_soldout", 단가 * 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_OUT_OF_STOCK"));

        assertThat(가짜_게이트웨이_설정.confirm호출).isEmpty();
        assertThat(재조회_재고(옵션)).isEqualTo(1);
    }

    @Test
    void 토스_통신이_실패하면_차감이_되돌아간다() throws Exception {
        가짜_게이트웨이_설정.초기화(단가 * 3);
        가짜_게이트웨이_설정.confirm이_터진다 = true;
        GoodsOption 옵션 = 옵션_저장("크림", 10);
        Order 주문 = 주문_저장(항목(옵션, 3));

        승인요청(주문.getOrderNo(), "pk_down", 단가 * 3)
                .andExpect(status().is5xxServerError());

        // 복원 코드는 없다. 롤백이 곧 복원이다(§2 결정 2).
        assertThat(재조회_재고(옵션)).isEqualTo(10);
        assertThat(재조회_상태(주문)).isEqualTo(Order.STATUS_PENDING);
    }

    @Test
    void 금액이_불일치하면_승인을_취소하고_차감이_되돌아간다() throws Exception {
        가짜_게이트웨이_설정.초기화(10);           // 토스가 알려온 승인액(조작됨)
        GoodsOption 옵션 = 옵션_저장("클렌저", 10);
        Order 주문 = 주문_저장(항목(옵션, 3));      // 우리가 계산한 진짜 금액은 48000

        승인요청(주문.getOrderNo(), "pk_tampered", 10)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));

        assertThat(가짜_게이트웨이_설정.cancel호출).containsExactly("pk_tampered");
        assertThat(재조회_재고(옵션)).isEqualTo(10);
        assertThat(재조회_상태(주문)).isEqualTo(Order.STATUS_PENDING);
    }

    @Test
    void 부분_품절이면_다른_상품도_깎이지_않는다() throws Exception {
        // A는 먼저 깎이고 B에서 실패한다(optionId 오름차순). 롤백이 A까지 되돌리지 않으면
        // "일부만 깎인 주문"이 남는다 — all-or-nothing의 회귀 방어다(§2 결정 3).
        가짜_게이트웨이_설정.초기화(단가 * 2);
        GoodsOption 옵션A = 옵션_저장("멀쩡한옵션", 10);
        GoodsOption 옵션B = 옵션_저장("품절옵션", 0);
        Order 주문 = 주문_저장(항목(옵션A, 1), 항목(옵션B, 1));

        승인요청(주문.getOrderNo(), "pk_partial", 단가 * 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_OUT_OF_STOCK"));

        assertThat(재조회_재고(옵션A)).isEqualTo(10);
        assertThat(재조회_재고(옵션B)).isEqualTo(0);
        assertThat(재조회_상태(주문)).isEqualTo(Order.STATUS_PENDING);
    }

    @Test
    void 옵션_없는_상품은_차감_없이_승인된다() throws Exception {
        // 재고 관리 단위는 옵션이다. optionId가 null인 항목은 차감 대상이 아니며,
        // 거르지 않으면 null 키로 차감을 시도해 승인 자체가 죽는다.
        가짜_게이트웨이_설정.초기화(단가);
        Goods 상품 = 상품_저장("옵션없는상품");
        Order 주문 = 주문_저장(new OrderItem(상품.getId(), null, "옵션없는상품", null, 단가, 1));

        승인요청(주문.getOrderNo(), "pk_nooption", 단가)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        assertThat(재조회_상태(주문)).isEqualTo(Order.STATUS_PAID);
    }

    @Test
    void 차감_뒤에도_결제_완료_전이가_유실되지_않는다() throws Exception {
        // 차감 쿼리에 clearAutomatically를 켜면 영속성 컨텍스트가 비워져 락과 함께 읽어 둔 Order가
        // detach되고, 뒤따르는 markPaid()가 더티체킹에서 빠져 조용히 유실된다(§3-2).
        // 그때 응답은 여전히 200 PAID이므로, 커밋된 DB를 새 트랜잭션으로 다시 읽어야 잡힌다.
        가짜_게이트웨이_설정.초기화(단가 * 3);
        GoodsOption 옵션 = 옵션_저장("에센스", 10);
        Order 주문 = 주문_저장(항목(옵션, 3));

        승인요청(주문.getOrderNo(), "pk_both", 단가 * 3)
                .andExpect(status().isOk());

        assertThat(재조회_상태(주문)).isEqualTo(Order.STATUS_PAID);
        assertThat(재조회_재고(옵션)).isEqualTo(7);
    }

    @Test
    void 이미_결제된_주문은_재고를_다시_깎지_않는다() throws Exception {
        // 상태 검사가 차감보다 앞이라는 순서 회귀. 뒤집히면 재승인마다 재고가 또 깎인다.
        가짜_게이트웨이_설정.초기화(단가 * 3);
        GoodsOption 옵션 = 옵션_저장("로션", 10);
        Order 주문 = 주문_저장(항목(옵션, 3));

        승인요청(주문.getOrderNo(), "pk_first", 단가 * 3).andExpect(status().isOk());
        assertThat(재조회_재고(옵션)).isEqualTo(7);

        승인요청(주문.getOrderNo(), "pk_second", 단가 * 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_CONFIRMED"));

        assertThat(재조회_재고(옵션)).isEqualTo(7);
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

    private int 재조회_재고(GoodsOption 옵션) {
        return goodsOptionRepository.findById(옵션.getId()).orElseThrow().getStock();
    }

    private String 재조회_상태(Order 주문) {
        return orderRepository.findByOrderNo(주문.getOrderNo()).orElseThrow().getStatus();
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

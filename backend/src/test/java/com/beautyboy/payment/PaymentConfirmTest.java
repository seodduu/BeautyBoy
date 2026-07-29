package com.beautyboy.payment;

import com.beautyboy.order.Order;
import com.beautyboy.order.OrderRepository;
import com.beautyboy.payment.dto.PaymentApproval;
import com.beautyboy.support.TestPersistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentConfirmTest {

    private static final Long 회원 = 1L;

    /**
     * 가짜 게이트웨이. 토스를 부르지 않고, 우리가 지정한 승인 금액을 돌려주고 취소 호출을 기록한다.
     * approvedAmount를 테스트마다 바꿔 "토스가 알려준 금액"을 조작한다 —
     * 이것이 금액 불일치 검증의 대상이다.
     */
    @TestConfiguration
    static class 가짜_게이트웨이_설정 {
        static int approvedAmount;
        static final List<String> canceledKeys = new CopyOnWriteArrayList<>();

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
                    canceledKeys.add(paymentKey);
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
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void 금액이_일치하면_결제완료로_전이하고_payment를_남긴다() throws Exception {
        가짜_게이트웨이_설정.canceledKeys.clear();
        가짜_게이트웨이_설정.approvedAmount = 16000;
        Order order = 결제대기_주문_저장(16000);

        승인요청(order.getOrderNo(), "pk_ok", 16000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(orderRepository.findByOrderNo(order.getOrderNo()).orElseThrow().getStatus())
                .isEqualTo(Order.STATUS_PAID);
        assertThat(paymentRepository.findAll()).hasSize(1);
        assertThat(가짜_게이트웨이_설정.canceledKeys).isEmpty();
    }

    @Test
    void 토스_승인액이_주문액과_다르면_취소를_부르고_409다() throws Exception {
        // 핵심 시나리오. 결제창에서 금액을 조작해 싸게 결제한 경우, 토스가 그 조작된 금액을 승인해 돌려준다.
        // 우리 주문의 payableAmount(16000)와 다르므로 승인을 취소하고 주문을 실패시킨다.
        가짜_게이트웨이_설정.canceledKeys.clear();
        가짜_게이트웨이_설정.approvedAmount = 10;          // 토스가 알려온 승인액(조작됨)
        Order order = 결제대기_주문_저장(16000);            // 우리가 계산한 진짜 금액

        승인요청(order.getOrderNo(), "pk_tampered", 10)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));

        TestPersistence.DB_왕복_강제(entityManager);

        // 승인을 반드시 취소했어야 한다 — 안 하면 돈은 빠져나갔는데 주문은 실패로 남는다.
        assertThat(가짜_게이트웨이_설정.canceledKeys).containsExactly("pk_tampered");
        // 주문은 결제대기로 남고, payment는 저장하지 않는다.
        assertThat(orderRepository.findByOrderNo(order.getOrderNo()).orElseThrow().getStatus())
                .isEqualTo(Order.STATUS_PENDING);
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    void 이미_결제된_주문에_다시_승인하면_409고_두_번_청구되지_않는다() throws Exception {
        가짜_게이트웨이_설정.canceledKeys.clear();
        가짜_게이트웨이_설정.approvedAmount = 16000;
        Order order = 결제대기_주문_저장(16000);

        승인요청(order.getOrderNo(), "pk_1", 16000).andExpect(status().isOk());

        TestPersistence.DB_왕복_강제(entityManager);

        // 두 번째 승인. 상태가 이미 PAID라 거부해야 한다.
        승인요청(order.getOrderNo(), "pk_2", 16000)
                .andExpect(status().isConflict());

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(paymentRepository.findAll()).hasSize(1);
    }

    @Test
    void 없는_주문번호면_404다() throws Exception {
        가짜_게이트웨이_설정.approvedAmount = 16000;

        승인요청("ORD-NONE", "pk_x", 16000)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void 남의_주문을_결제하려_하면_404다() throws Exception {
        // 주문번호만 알면 남의 결제를 확정시킬 수 있으면 안 된다.
        가짜_게이트웨이_설정.approvedAmount = 16000;
        Order order = 결제대기_주문_저장(16000);   // 회원(1)의 주문

        String body = objectMapper.writeValueAsString(Map.of(
                "orderNo", order.getOrderNo(), "paymentKey", "pk_x", "amount", 16000));
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .with(authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                999L, null,
                                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions 승인요청(
            String orderNo, String paymentKey, int amount) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "orderNo", orderNo, "paymentKey", paymentKey, "amount", amount));
        return mockMvc.perform(post("/api/v1/payments/confirm")
                .with(authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        회원, null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** payableAmount를 원하는 값으로 만들기 위해 단가=payable, 수량 1짜리 주문을 직접 만든다. */
    private Order 결제대기_주문_저장(int payableAmount) {
        Order order = new Order("ORD-" + System.nanoTime(), 회원, "홍길동", "010-0000-0000",
                "06234", "서울시 강남구", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new com.beautyboy.order.OrderItem(
                1L, null, "토너", null, payableAmount, 1));
        return orderRepository.saveAndFlush(order);
    }
}

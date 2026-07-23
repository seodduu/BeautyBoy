package com.beautyboy.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderQueryTest {

    private static final Long 회원 = 1L;
    private static final Long 다른회원 = 2L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    OrderRepository orderRepository;

    @Test
    void 내_주문_목록을_최신순으로_준다() throws Exception {
        주문_저장(회원, "토너");
        주문_저장(회원, "크림");

        mockMvc.perform(get("/api/v1/orders").with(로그인(회원)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void 남의_주문은_목록에_섞이지_않는다() throws Exception {
        주문_저장(회원, "내 토너");
        주문_저장(다른회원, "남의 크림");

        mockMvc.perform(get("/api/v1/orders").with(로그인(회원)))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 주문_상세를_상품_목록과_함께_준다() throws Exception {
        String orderNo = 주문_저장(회원, "그린티 토너").getOrderNo();

        mockMvc.perform(get("/api/v1/orders/" + orderNo).with(로그인(회원)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.items[0].goodsName").value("그린티 토너"));
    }

    @Test
    void 남의_주문_상세는_404다() throws Exception {
        String orderNo = 주문_저장(회원, "토너").getOrderNo();

        mockMvc.perform(get("/api/v1/orders/" + orderNo).with(로그인(다른회원)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Order 주문_저장(Long memberId, String goodsName) {
        Order order = new Order("ORD-" + System.nanoTime(), memberId, "홍길동", "010-0000-0000",
                "06234", "서울시", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(1L, null, goodsName, null, 16000, 1));
        return orderRepository.saveAndFlush(order);
    }
}

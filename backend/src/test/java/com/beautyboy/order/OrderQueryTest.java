package com.beautyboy.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    void 남의_주문은_목록에_섞이지_않는다() throws Exception {
        주문_저장(회원, "내 토너");
        주문_저장(다른회원, "남의 크림");

        mockMvc.perform(get("/api/v1/orders").with(로그인(회원)))
                .andExpect(jsonPath("$.data.content.length()").value(1));
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

    @Test
    @DisplayName("주문 목록은 기본 10건씩 페이지로 준다 — 12건이면 1페이지에 10건, hasNext=true")
    void 기본_페이지_크기는_10() throws Exception {
        for (int i = 0; i < 12; i++) {
            주문_저장(회원, "상품" + i);
        }

        mockMvc.perform(get("/api/v1/orders").with(로그인(회원)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(10))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(12))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    @DisplayName("size가 상한을 넘으면 100으로 깎는다 — /goods·/qna와 같은 상한")
    void size_상한은_100() throws Exception {
        주문_저장(회원, "토너");

        mockMvc.perform(get("/api/v1/orders?size=100000").with(로그인(회원)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    @DisplayName("size가 0 이하면 1로 올린다 — PageRequest가 예외를 던져 500이 되지 않게")
    void size_하한은_1() throws Exception {
        주문_저장(회원, "토너");

        mockMvc.perform(get("/api/v1/orders?size=0").with(로그인(회원)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(1));
    }

    @Test
    @DisplayName("같은 시각 주문 두 건도 페이지 경계에서 중복·누락되지 않는다 — id desc 2차 정렬 키")
    void 동시각_주문의_페이지_경계() throws Exception {
        LocalDateTime 같은시각 = LocalDateTime.now();
        주문_저장(회원, "먼저 저장", 같은시각);
        주문_저장(회원, "나중 저장", 같은시각);

        String 응답0페이지 = mockMvc.perform(get("/api/v1/orders?size=1&page=0").with(로그인(회원)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String 응답1페이지 = mockMvc.perform(get("/api/v1/orders?size=1&page=1").with(로그인(회원)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String orderNo0페이지 = com.jayway.jsonpath.JsonPath.read(응답0페이지, "$.data.content[0].orderNo");
        String orderNo1페이지 = com.jayway.jsonpath.JsonPath.read(응답1페이지, "$.data.content[0].orderNo");

        assertThat(orderNo0페이지).isNotEqualTo(orderNo1페이지);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Order 주문_저장(Long memberId, String goodsName) {
        return 주문_저장(memberId, goodsName, LocalDateTime.now());
    }

    private Order 주문_저장(Long memberId, String goodsName, LocalDateTime orderedAt) {
        Order order = new Order("ORD-" + System.nanoTime(), memberId, "홍길동", "010-0000-0000",
                "06234", "서울시", "101호", "NORMAL", orderedAt);
        order.addItem(new OrderItem(1L, null, goodsName, null, 16000, 1));
        return orderRepository.saveAndFlush(order);
    }
}

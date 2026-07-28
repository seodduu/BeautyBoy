package com.beautyboy.common;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.order.Order;
import com.beautyboy.order.OrderItem;
import com.beautyboy.order.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link PageRequests} 도입 전에는 하한이 없던 세 엔드포인트(goods·admin goods·search)가
 * {@code page}·{@code size} 음수/0에서 500을 냈다. 이 다섯 케이스가 그 버그의 실제 사양이다.
 *
 * <p>§2 결정 1 참고: {@code size=0}을 400으로 막지 않는다 — 나머지 네 곳이 이미 "조여서 처리"이므로
 * 판정 주체를 하나로 모으는 것이 목적이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PageParamClampTest {

    private static final Long 회원 = 1L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    OrderRepository orderRepository;

    @Test
    @DisplayName("상품 목록 size=-1은 500이 아니라 200 — clamp가 PageRequest보다 먼저 잡는다")
    void 상품목록_size_음수() throws Exception {
        상품_저장("토너");

        mockMvc.perform(get("/api/v1/goods").param("size", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(1));
    }

    @Test
    @DisplayName("상품 목록 page=-1은 500이 아니라 200, page는 0")
    void 상품목록_page_음수() throws Exception {
        상품_저장("토너");

        mockMvc.perform(get("/api/v1/goods").param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    @DisplayName("검색 size=0은 빈 페이지가 아니라 1건짜리 페이지 — totalPages가 0이 되지 않는다")
    void 검색_size_0() throws Exception {
        상품_저장("그린티 수분 토너");

        mockMvc.perform(get("/api/v1/search").param("q", "토너").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(greaterThan(0)));
    }

    @Test
    @DisplayName("상품 목록 size=1000은 여전히 100으로 잘린다 — 상한 동작은 그대로")
    void 상품목록_size_상한() throws Exception {
        상품_저장("토너");

        mockMvc.perform(get("/api/v1/goods").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    @DisplayName("주문 목록의 기존 동작은 바뀌지 않는다 — 이미 옳았던 곳을 건드리지 않았다는 증거")
    void 주문목록_동작보존() throws Exception {
        주문_저장(회원, "토너");

        mockMvc.perform(get("/api/v1/orders").param("page", "-1").param("size", "-1").with(로그인(회원)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1));
    }

    @Test
    @DisplayName("문의 목록 page=-1은 500이 아니라 200 — QnaService.list에도 하한이 생겼다")
    void 문의목록_page_음수() throws Exception {
        Goods goods = 상품_저장("토너");

        mockMvc.perform(get("/api/v1/qna").param("goodsNo", String.valueOf(goods.getId())).param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    @DisplayName("admin 문의 목록 page=-1은 500이 아니라 200 — size만 조이던 곳의 나머지 절반")
    void admin_문의목록_page_음수() throws Exception {
        mockMvc.perform(get("/api/v1/admin/qna").param("page", "-1").with(관리자_로그인()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0));
    }

    private Goods 상품_저장(String name) {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        return goodsRepository.save(new Goods(brand, "C001001001", name, "요약", "https://img.example/x.jpg", 10000, 10000));
    }

    private Order 주문_저장(Long memberId, String goodsName) {
        Order order = new Order("ORD-" + System.nanoTime(), memberId, "홍길동", "010-0000-0000",
                "06234", "서울시", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(1L, null, goodsName, null, 16000, 1));
        return orderRepository.saveAndFlush(order);
    }

    private static RequestPostProcessor 로그인(Long memberId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                memberId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static RequestPostProcessor 관리자_로그인() {
        return authentication(new UsernamePasswordAuthenticationToken(
                회원, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}

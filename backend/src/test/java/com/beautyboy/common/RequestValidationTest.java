package com.beautyboy.common;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 1-4: 손님 경로 Bean Validation.
 *
 * <p>앞 8건은 "이제 막힌다"(구조적 결손 → 400 INVALID_INPUT), 뒤 3건은 "애노테이션이 도메인 코드를
 * 가로채지 않는다"(§2 결정 3의 경계 고정)를 못 박는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RequestValidationTest {

    private static final Long 회원 = 1L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    // --- 구조적 결손: 이제 400 INVALID_INPUT으로 막힌다 (예전에는 NPE·DB 예외로 500) ---

    @Test
    @DisplayName("결제 승인: orderNo가 비면 400 INVALID_INPUT")
    void 결제_orderNo_공백() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("orderNo", "", "paymentKey", "pk", "amount", 1000));

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("결제 승인: paymentKey가 null이면 400 INVALID_INPUT — 토스에 null을 들고 가지 않는다")
    void 결제_paymentKey_null() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("orderNo", "ORD-1");
        body.put("paymentKey", null);
        body.put("amount", 1000);

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("주문 생성: 수령인이 비면 400 INVALID_INPUT")
    void 주문_수령인_공백() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);
        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("goodsNo", goodsId, "quantity", 1)),
                "deliveryType", "NORMAL",
                "receiverName", "",
                "receiverPhone", "010-1234-5678",
                "zipcode", "06234",
                "address1", "서울시 강남구",
                "address2", "101호"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("주문 생성: address1이 200자를 넘으면 400 INVALID_INPUT — DB 예외(500)로 새지 않는다")
    void 주문_주소_길이초과() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);
        String 긴주소 = "가".repeat(201);
        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("goodsNo", goodsId, "quantity", 1)),
                "deliveryType", "NORMAL",
                "receiverName", "홍길동",
                "receiverPhone", "010-1234-5678",
                "zipcode", "06234",
                "address1", 긴주소,
                "address2", "101호"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("주문 생성: 항목의 goodsNo가 null이면 400 INVALID_INPUT")
    void 주문_항목_goodsNo_null() throws Exception {
        Map<String, Object> item = new HashMap<>();
        item.put("goodsNo", null);
        item.put("quantity", 1);
        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(item),
                "deliveryType", "NORMAL",
                "receiverName", "홍길동",
                "receiverPhone", "010-1234-5678",
                "zipcode", "06234",
                "address1", "서울시 강남구",
                "address2", "101호"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("장바구니 담기: goodsNo가 null이면 400 INVALID_INPUT")
    void 담기_goodsNo_null() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("goodsNo", null);
        body.put("optionNo", null);
        body.put("quantity", 1);

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("리뷰 작성: 내용이 공백이면 400 INVALID_INPUT")
    void 리뷰_내용_공백() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);
        String body = objectMapper.writeValueAsString(Map.of("goodsNo", goodsId, "rating", 5, "content", "   "));

        mockMvc.perform(post("/api/v1/reviews")
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("문의 작성: 질문이 공백이면 400 INVALID_INPUT")
    void 문의_질문_공백() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);
        String body = objectMapper.writeValueAsString(Map.of("goodsNo", goodsId, "question", " ", "isSecret", false));

        mockMvc.perform(post("/api/v1/qna")
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // --- 경계 고정: 도메인 판정은 그대로 서비스가 한다 (§2 결정 3) ---

    @Test
    @DisplayName("수량 0은 여전히 CART_QUANTITY_INVALID — 애노테이션이 도메인 코드를 가로채지 않는다")
    void 수량_0은_도메인_코드() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);
        Map<String, Object> body = new HashMap<>();
        body.put("goodsNo", goodsId);
        body.put("optionNo", null);
        body.put("quantity", 0);

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_QUANTITY_INVALID"));
    }

    @Test
    @DisplayName("빈 항목 주문은 여전히 CART_EMPTY")
    void 빈_항목은_도메인_코드() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(),
                "deliveryType", "NORMAL",
                "receiverName", "홍길동",
                "receiverPhone", "010-1234-5678",
                "zipcode", "06234",
                "address1", "서울시 강남구",
                "address2", "101호"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
    }

    @Test
    @DisplayName("평점 6은 여전히 ReviewService가 판정한다 — detail(필드 오류)이 없는 INVALID_INPUT")
    void 평점_범위는_서비스가_판정한다() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);
        String body = objectMapper.writeValueAsString(Map.of("goodsNo", goodsId, "rating", 6, "content", "범위 밖"));

        mockMvc.perform(post("/api/v1/reviews")
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                // Bean Validation이 잡았다면 GlobalExceptionHandler가 fieldErrors를 detail에 싣는다.
                // detail이 비어 있다는 것이 "서비스가 판정했다"는 증거다.
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Long 상품_저장(String name, int salePrice) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        return goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", salePrice, salePrice)).getId();
    }
}

package com.beautyboy.cart;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 장바구니 API 테스트.
 *
 * <p>인증은 SecurityContext에 memberId를 principal로 넣어 흉내낸다 —
 * JwtAuthenticationFilter가 그렇게 세팅하므로(@AuthenticationPrincipal Long memberId)
 * 실제 토큰을 만들지 않아도 컨트롤러가 보는 값은 같다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CartApiTest {

    private static final Long 회원 = 1L;
    private static final Long 다른회원 = 2L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    void 담기와_조회가_동작한다() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);

        담기(회원, goodsId, 2).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/cart/items").with(로그인(회원)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].goodsName").value("토너"))
                .andExpect(jsonPath("$.data[0].quantity").value(2))
                // 가격은 저장하지 않고 조회 시점의 상품 판매가를 보여준다 —
                // 장바구니는 아직 구매가 아니므로 스냅샷을 뜰 시점이 아니다.
                .andExpect(jsonPath("$.data[0].unitPrice").value(16000));
    }

    @Test
    void 같은_상품을_또_담으면_수량이_합쳐진다() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);

        담기(회원, goodsId, 2).andExpect(status().isCreated());
        담기(회원, goodsId, 3).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/cart/items").with(로그인(회원)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].quantity").value(5));
    }

    @Test
    void 수량이_0_이하면_400과_CART_QUANTITY_INVALID() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);

        담기(회원, goodsId, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_QUANTITY_INVALID"));
    }

    @Test
    void 없는_상품을_담으면_404와_GOODS_NOT_FOUND() throws Exception {
        담기(회원, 999999L, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"));
    }

    @Test
    void 수량_변경이_동작한다() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);
        담기(회원, goodsId, 1);
        Long cartItemId = 첫_장바구니_id(회원);

        mockMvc.perform(patch("/api/v1/cart/items/" + cartItemId)
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 4))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/cart/items").with(로그인(회원)))
                .andExpect(jsonPath("$.data[0].quantity").value(4));
    }

    @Test
    void 삭제가_동작한다() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);
        담기(회원, goodsId, 1);
        Long cartItemId = 첫_장바구니_id(회원);

        mockMvc.perform(delete("/api/v1/cart/items/" + cartItemId).with(로그인(회원)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cart/items").with(로그인(회원)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 남의_장바구니_항목은_수정할_수_없다() throws Exception {
        // 여기가 뚫리면 남의 장바구니를 조작할 수 있다. id만 알면 되는 가장 흔한 취약점이다.
        Long goodsId = 상품_저장("토너", 16000);
        담기(회원, goodsId, 1);
        Long cartItemId = 첫_장바구니_id(회원);

        mockMvc.perform(patch("/api/v1/cart/items/" + cartItemId)
                        .with(로그인(다른회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 99))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
    }

    @Test
    void 루틴_일괄_담기가_여러_건을_한_번에_넣는다() throws Exception {
        Long 토너 = 상품_저장("토너", 16000);
        Long 크림 = 상품_저장("크림", 24000);

        String body = objectMapper.writeValueAsString(Map.of("items", java.util.List.of(
                Map.of("goodsNo", 토너, "quantity", 1),
                Map.of("goodsNo", 크림, "quantity", 2))));

        mockMvc.perform(post("/api/v1/cart/items/bulk")
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/cart/items").with(로그인(회원)))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/cart/items")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions 담기(Long memberId, Long goodsNo, int quantity)
            throws Exception {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("goodsNo", goodsNo);
        body.put("quantity", quantity);
        return mockMvc.perform(post("/api/v1/cart/items")
                .with(로그인(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Long 첫_장바구니_id(Long memberId) throws Exception {
        String json = mockMvc.perform(get("/api/v1/cart/items").with(로그인(memberId)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get(0).get("cartItemId").asLong();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Long 상품_저장(String name, int salePrice) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        return goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", salePrice, salePrice)).getId();
    }
}

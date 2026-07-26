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
    @Autowired
    CartItemRepository cartItemRepository;

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
    void 옵션을_지정하지_않고_담으면_해석된_대표_옵션이_저장된다() throws Exception {
        // 담긴 순간에 확정한다(Task 4-18). null로 저장하고 읽을 때마다 다시 해석하면
        // sortOrder가 바뀌거나 옵션이 삭제될 때 장바구니 내용이 조용히 달라진다.
        Long goodsId = 상품_저장("토너", 16000);
        Long 옵션_200ml = 옵션_저장(goodsId, "200ml", 0, 150, 1);
        옵션_저장(goodsId, "300ml", 3000, 40, 2);

        담기(회원, goodsId, 1).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/cart/items").with(로그인(회원)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].optionNo").value(옵션_200ml))
                .andExpect(jsonPath("$.data[0].optionName").value("200ml"))
                .andExpect(jsonPath("$.data[0].unitPrice").value(16000));
    }

    @Test
    void 루틴과_상세에서_같은_옵션을_담으면_한_행으로_합쳐진다() throws Exception {
        // 루틴 전체담기는 optionNo=null, 상세는 optionNo를 실어 보낸다. 해석된 optionId로
        // 중복을 판정하므로 같은 옵션이면 한 줄이 된다 — 예전에는 (goodsId, null)과
        // (goodsId, optionId)로 갈라져 같은 옵션이 두 줄로 보였다.
        Long goodsId = 상품_저장("토너", 16000);
        Long 옵션_200ml = 옵션_저장(goodsId, "200ml", 0, 150, 1);

        담기(회원, goodsId, 1).andExpect(status().isCreated());              // 루틴 경로(optionNo 없음)
        담기(회원, goodsId, 옵션_200ml, 2).andExpect(status().isCreated());   // 상세 경로(optionNo 지정)

        mockMvc.perform(get("/api/v1/cart/items").with(로그인(회원)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].optionNo").value(옵션_200ml))
                .andExpect(jsonPath("$.data[0].quantity").value(3));
    }

    @Test
    void 옵션이_없는_상품은_optionId가_null로_남는다() throws Exception {
        // 회귀 방어: 해석 결과가 없는 경우(옵션 자체가 없는 상품)는 예전 동작 그대로다.
        Long goodsId = 상품_저장("옵션 없는 토너", 16000);

        담기(회원, goodsId, 1).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/cart/items").with(로그인(회원)))
                .andExpect(jsonPath("$.data[0].optionNo").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data[0].optionName").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void 레거시_NULL_option_id_행은_대표_옵션으로_자기모순_없이_응답한다() throws Exception {
        // Task 4-18 이전에 담긴 행을 흉내낸다: 옵션이 있는 상품인데 option_id가 NULL로 저장된
        // 레거시 행. CartService.add()를 거치지 않고 리포지토리로 직접 그 상태를 만든다.
        // 고치기 전에는 optionNo=null인데 optionName="200ml"이 나오는 자기모순 응답이었다.
        Long goodsId = 상품_저장("토너", 16000);
        Long 옵션_200ml = 옵션_저장(goodsId, "200ml", 0, 150, 1);
        옵션_저장(goodsId, "300ml", 3000, 40, 2);
        cartItemRepository.save(new CartItem(회원, goodsId, null, 1));

        mockMvc.perform(get("/api/v1/cart/items").with(로그인(회원)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].optionNo").value(옵션_200ml))
                .andExpect(jsonPath("$.data[0].optionName").value("200ml"));
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/cart/items")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions 담기(Long memberId, Long goodsNo, int quantity)
            throws Exception {
        return 담기(memberId, goodsNo, null, quantity);
    }

    private org.springframework.test.web.servlet.ResultActions 담기(
            Long memberId, Long goodsNo, Long optionNo, int quantity) throws Exception {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("goodsNo", goodsNo);
        body.put("optionNo", optionNo);
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

    private Long 옵션_저장(Long goodsId, String name, int addPrice, int stock, int sortOrder) {
        Goods goods = goodsRepository.findById(goodsId).orElseThrow();
        goods.getOptions().add(new com.beautyboy.catalog.GoodsOption(goods, name, addPrice, stock, sortOrder));
        // goods가 이미 영속 상태라 save()는 merge를 타고, 새 옵션은 복사본으로 persist된다 —
        // 넘긴 인스턴스가 아니라 저장 결과의 컬렉션에서 id를 읽어야 한다.
        Goods saved = goodsRepository.saveAndFlush(goods);
        return saved.getOptions().stream()
                .filter(o -> o.getName().equals(name))
                .map(com.beautyboy.catalog.GoodsOption::getId)
                .findFirst()
                .orElseThrow();
    }
}

package com.beautyboy.wishlist;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WishlistApiTest {

    private static final Long 회원 = 1L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    void 찜하고_목록에서_확인한다() throws Exception {
        Long goodsId = 상품_저장();

        mockMvc.perform(post("/api/v1/wishlist/" + goodsId).with(로그인(회원)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/wishlist").with(로그인(회원)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 찜_해제가_동작한다() throws Exception {
        Long goodsId = 상품_저장();
        mockMvc.perform(post("/api/v1/wishlist/" + goodsId).with(로그인(회원)));

        mockMvc.perform(delete("/api/v1/wishlist/" + goodsId).with(로그인(회원)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/wishlist").with(로그인(회원)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 같은_상품을_두_번_찜하면_409와_WISHLIST_ALREADY_ADDED() throws Exception {
        Long goodsId = 상품_저장();
        mockMvc.perform(post("/api/v1/wishlist/" + goodsId).with(로그인(회원)));

        mockMvc.perform(post("/api/v1/wishlist/" + goodsId).with(로그인(회원)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WISHLIST_ALREADY_ADDED"));
    }

    @Test
    void 없는_상품을_찜하면_404와_GOODS_NOT_FOUND() throws Exception {
        mockMvc.perform(post("/api/v1/wishlist/999999").with(로그인(회원)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"));
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/wishlist")).andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Long 상품_저장() {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        return goodsRepository.save(
                new Goods(brand, "C001001001", "토너", null, "https://img/x.jpg", 16000, 16000)).getId();
    }
}

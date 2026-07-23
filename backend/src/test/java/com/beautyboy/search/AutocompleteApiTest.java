package com.beautyboy.search;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AutocompleteApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    void 접두사로_시작하는_상품명을_준다() throws Exception {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        goodsRepository.save(new Goods(brand, "C001001001", "수분폭탄 토너", null, "https://img/1.jpg", 10000, 10000));
        goodsRepository.save(new Goods(brand, "C001003001", "영양 크림", null, "https://img/2.jpg", 10000, 10000));

        mockMvc.perform(get("/api/v1/search/autocomplete").param("q", "수분"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value("수분폭탄 토너"));
    }

    @Test
    void 최대_10건까지만_준다() throws Exception {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        for (int i = 1; i <= 12; i++) {
            goodsRepository.save(new Goods(brand, "C001001001", "토너 " + i, null, "https://img/x.jpg", 10000, 10000));
        }

        mockMvc.perform(get("/api/v1/search/autocomplete").param("q", "토너"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(10));
    }

    @Test
    void 검색어가_2자_미만이면_에러가_아니라_빈_목록이다() throws Exception {
        // 자동완성은 타이핑 중에 매 글자 호출된다. 첫 글자마다 400을 뱉으면
        // 프론트 콘솔이 에러로 뒤덮이고 정상 흐름과 장애를 구분할 수 없게 된다.
        // 그래서 /search와 달리 조용히 빈 목록을 준다.
        mockMvc.perform(get("/api/v1/search/autocomplete").param("q", "토"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}

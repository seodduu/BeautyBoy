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

/**
 * 검색 API 테스트.
 *
 * <p>여기서 도는 구현은 LIKE 쪽이다(H2에 FULLTEXT가 없다). 그래서 이 테스트가 검증하는 것은
 * <b>서비스 계약</b>(파라미터 검증·정렬·페이징·응답 형태)이지 FULLTEXT 질의의 정확성이 아니다.
 * FULLTEXT 자체는 MysqlFulltextSearchIntegrationTest(@Tag("integration"))가 실 MySQL에서 본다.
 *
 * <p>픽스처가 catalog 엔티티를 쓰는 것은 의도적이다 — 검색 대상이 상품이므로 테스트에는 상품이 있어야 한다.
 * 운영 코드(search 패키지)는 catalog 타입을 import하지 않는다는 규칙과 별개다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SearchApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    void 상품명에_검색어가_들어간_상품을_찾는다() throws Exception {
        Brand brand = brandRepository.save(new Brand("이니스프리", null));
        goodsRepository.save(new Goods(brand, "C001001001", "그린티 수분 토너", null, "https://img/1.jpg", 20000, 16000));
        goodsRepository.save(new Goods(brand, "C001003001", "퍼펙트 로션", null, "https://img/2.jpg", 30000, 30000));

        mockMvc.perform(get("/api/v1/search").param("q", "토너"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("그린티 수분 토너"));
    }

    @Test
    void 브랜드명으로도_검색된다() throws Exception {
        Brand brand = brandRepository.save(new Brand("닥터지", null));
        goodsRepository.save(new Goods(brand, "C001003001", "레드블레미쉬 크림", null, "https://img/3.jpg", 30000, 24000));

        mockMvc.perform(get("/api/v1/search").param("q", "닥터지"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].brandName").value("닥터지"));
    }

    @Test
    void 숨김_상품은_검색되지_않는다() throws Exception {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        Goods hidden = new Goods(brand, "C001001001", "숨김 토너", null, "https://img/4.jpg", 10000, 10000);
        hidden.hide();
        goodsRepository.save(hidden);

        mockMvc.perform(get("/api/v1/search").param("q", "토너"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 검색어가_2자_미만이면_400과_SEARCH_QUERY_TOO_SHORT() throws Exception {
        // ngram_token_size=2라 1자 검색어는 FULLTEXT에서 어차피 아무것도 매칭되지 않는다.
        // 빈 결과를 주는 대신 이유를 알려주고 끊는다.
        mockMvc.perform(get("/api/v1/search").param("q", "토"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_QUERY_TOO_SHORT"));
    }

    @Test
    void 지원하지_않는_정렬이면_400과_SEARCH_INVALID_SORT() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "토너").param("sort", "없는정렬"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_INVALID_SORT"));
    }

    @Test
    void 가격오름차순_정렬이_동작한다() throws Exception {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        goodsRepository.save(new Goods(brand, "C001001001", "비싼 토너", null, "https://img/5.jpg", 50000, 40000));
        goodsRepository.save(new Goods(brand, "C001001001", "싼 토너", null, "https://img/6.jpg", 10000, 8000));

        mockMvc.perform(get("/api/v1/search").param("q", "토너").param("sort", "priceAsc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("싼 토너"))
                .andExpect(jsonPath("$.data.content[1].name").value("비싼 토너"));
    }

    @Test
    void 페이징_정보가_PageResponse_계약대로_나온다() throws Exception {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        for (int i = 1; i <= 3; i++) {
            goodsRepository.save(new Goods(brand, "C001001001", "토너 " + i, null, "https://img/x.jpg", 10000, 10000));
        }

        mockMvc.perform(get("/api/v1/search").param("q", "토너").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }
}

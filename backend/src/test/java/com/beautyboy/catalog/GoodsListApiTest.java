package com.beautyboy.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class GoodsListApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    PromotionRepository promotionRepository;
    @Autowired
    TagRepository tagRepository;
    @Autowired
    GoodsTagRepository goodsTagRepository;
    @Autowired
    EntityManagerFactory entityManagerFactory;
    @jakarta.persistence.PersistenceContext
    jakarta.persistence.EntityManager entityManager;

    // ---------- 카테고리 접두사 필터 ----------

    @Test
    void 카테고리_접두사로_필터링한다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        카테고리_저장("C001002001", 3);
        상품_저장(brand, "C001001001", "토너", 10000, 10000);
        상품_저장(brand, "C001002001", "로션", 10000, 10000);

        mockMvc.perform(get("/api/v1/goods").param("categoryCode", "C001001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("토너"));
    }

    // ---------- 정렬 5종 ----------

    @Test
    void popular_정렬은_view_count_내림차순이다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        Goods low = 상품_저장(brand, "C001001001", "낮은조회", 10000, 10000);
        low.increaseViewCount(1);
        Goods high = 상품_저장(brand, "C001001001", "높은조회", 10000, 10000);
        high.increaseViewCount(100);
        goodsRepository.saveAll(List.of(low, high));

        MvcResult result = mockMvc.perform(get("/api/v1/goods").param("sort", "popular"))
                .andExpect(status().isOk())
                .andReturn();

        List<String> names = 이름목록(result);
        assertThat(names).containsExactly("높은조회", "낮은조회");
    }

    @Test
    void new_정렬은_created_at_내림차순이다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        상품_저장(brand, "C001001001", "먼저생성", 10000, 10000);
        상품_저장(brand, "C001001001", "나중생성", 10000, 10000);

        MvcResult result = mockMvc.perform(get("/api/v1/goods").param("sort", "new"))
                .andExpect(status().isOk())
                .andReturn();

        List<String> names = 이름목록(result);
        assertThat(names).containsExactly("나중생성", "먼저생성");
    }

    @Test
    void sales_정렬은_sales_count_내림차순이다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        Goods low = 상품_저장(brand, "C001001001", "적게팔림", 10000, 10000);
        low.increaseSalesCount(1);
        Goods high = 상품_저장(brand, "C001001001", "많이팔림", 10000, 10000);
        high.increaseSalesCount(100);
        goodsRepository.saveAll(List.of(low, high));

        MvcResult result = mockMvc.perform(get("/api/v1/goods").param("sort", "sales"))
                .andExpect(status().isOk())
                .andReturn();

        List<String> names = 이름목록(result);
        assertThat(names).containsExactly("많이팔림", "적게팔림");
    }

    @Test
    void priceAsc_정렬은_sale_price_오름차순이다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        상품_저장(brand, "C001001001", "비쌈", 20000, 20000);
        상품_저장(brand, "C001001001", "저렴함", 10000, 10000);

        MvcResult result = mockMvc.perform(get("/api/v1/goods").param("sort", "priceAsc"))
                .andExpect(status().isOk())
                .andReturn();

        List<String> names = 이름목록(result);
        assertThat(names).containsExactly("저렴함", "비쌈");
    }

    @Test
    void discount_정렬은_할인율_내림차순이다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        상품_저장(brand, "C001001001", "할인없음", 10000, 10000);   // 0%
        상품_저장(brand, "C001001001", "소폭할인", 10000, 9000);    // 10%
        상품_저장(brand, "C001001001", "대폭할인", 10000, 5000);    // 50%

        MvcResult result = mockMvc.perform(get("/api/v1/goods").param("sort", "discount"))
                .andExpect(status().isOk())
                .andReturn();

        List<String> names = 이름목록(result);
        assertThat(names).containsExactly("대폭할인", "소폭할인", "할인없음");
    }

    // ---------- 페이지네이션 ----------

    @Test
    void 페이지네이션_25건을_10개씩_잘라_2번째_페이지를_돌려준다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        for (int i = 0; i < 25; i++) {
            상품_저장(brand, "C001001001", "상품" + i, 10000, 10000);
        }

        mockMvc.perform(get("/api/v1/goods").param("size", "10").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(10))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(25))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    // ---------- 동점 정렬 안정성 ----------

    @Test
    void 동점_상품은_page0과_page1이_겹치지_않는다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        for (int i = 0; i < 5; i++) {
            상품_저장(brand, "C001001001", "동점상품" + i, 10000, 10000);
        }

        MvcResult page0 = mockMvc.perform(get("/api/v1/goods").param("size", "3").param("page", "0"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult page1 = mockMvc.perform(get("/api/v1/goods").param("size", "3").param("page", "1"))
                .andExpect(status().isOk())
                .andReturn();

        List<Long> page0Ids = goodsNo목록(page0);
        List<Long> page1Ids = goodsNo목록(page1);
        assertThat(page0Ids).doesNotContainAnyElementsOf(page1Ids);
    }

    // ---------- 가격 필터 경계 ----------

    @Test
    void 가격_필터는_경계값을_포함한다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        상품_저장(brand, "C001001001", "만원", 10000, 10000);
        상품_저장(brand, "C001001001", "이만원", 20000, 20000);
        상품_저장(brand, "C001001001", "삼만원", 30000, 30000);

        mockMvc.perform(get("/api/v1/goods").param("minPrice", "10000").param("maxPrice", "20000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    // ---------- 배지 파생 ----------

    @Test
    void 유효기간_내인_프로모션의_배지만_붙는다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        Goods active = 상품_저장(brand, "C001001001", "진행중할인", 10000, 10000);
        Goods ended = 상품_저장(brand, "C001001001", "종료된할인", 10000, 10000);

        Promotion activePromotion = promotionRepository.save(
                new Promotion("여름세일", "SALE", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)));
        Promotion endedPromotion = promotionRepository.save(
                new Promotion("작년세일", "SALE", LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(20)));

        entityManager.persist(new PromotionGoods(activePromotion.getId(), active.getId()));
        entityManager.persist(new PromotionGoods(endedPromotion.getId(), ended.getId()));
        entityManager.flush();

        MvcResult result = mockMvc.perform(get("/api/v1/goods"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("content");
        boolean activeHasBadge = false;
        boolean endedHasBadge = false;
        for (JsonNode node : content) {
            if (node.get("name").asText().equals("진행중할인")) {
                activeHasBadge = node.get("badges").toString().contains("SALE");
            }
            if (node.get("name").asText().equals("종료된할인")) {
                endedHasBadge = node.get("badges").toString().contains("SALE");
            }
        }
        assertThat(activeHasBadge).isTrue();
        assertThat(endedHasBadge).isFalse();
    }

    // ---------- 태그 ----------

    @Test
    void 목록응답에_태그가_실린다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        Goods goods = 상품_저장(brand, "C001001001", "토너", 10000, 10000);
        Tag tag = tagRepository.save(new Tag("저자극", "EFFECT", "gentle", 0));
        goodsTagRepository.save(new GoodsTag(goods.getId(), tag.getId(), null, 0));

        mockMvc.perform(get("/api/v1/goods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].tags[0].slug").value("gentle"))
                .andExpect(jsonPath("$.data.content[0].tags[0].kind").value("EFFECT"));
    }

    @Test
    void 태그가_없는_상품은_목록에서_빈_배열이다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        상품_저장(brand, "C001001001", "토너", 10000, 10000);

        mockMvc.perform(get("/api/v1/goods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].tags").isArray())
                .andExpect(jsonPath("$.data.content[0].tags.length()").value(0));
    }

    // ---------- HIDDEN 제외 ----------

    @Test
    void HIDDEN_상품은_목록에서_제외된다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        상품_저장(brand, "C001001001", "노출됨", 10000, 10000);
        Goods hidden = 상품_저장(brand, "C001001001", "숨김", 10000, 10000);
        hidden.hide();
        goodsRepository.save(hidden);

        mockMvc.perform(get("/api/v1/goods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("노출됨"));
    }

    // ---------- 미래 필드 기본값 ----------

    @Test
    void 미래_필드는_기본값을_낸다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        상품_저장(brand, "C001001001", "상품", 10000, 10000);

        mockMvc.perform(get("/api/v1/goods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].rating").value(0.0))
                .andExpect(jsonPath("$.data.content[0].reviewCount").value(0))
                .andExpect(jsonPath("$.data.content[0].wished").value(false))
                .andExpect(jsonPath("$.data.content[0].todayDreamAvailable").value(false));
    }

    // ---------- 정렬/사이즈 오류 처리 ----------

    @Test
    void sort가_bogus면_400과_GOODS_INVALID_SORT를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/goods").param("sort", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GOODS_INVALID_SORT"));
    }

    @Test
    void size가_1000이면_100으로_클램프된다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        for (int i = 0; i < 5; i++) {
            상품_저장(brand, "C001001001", "상품" + i, 10000, 10000);
        }

        mockMvc.perform(get("/api/v1/goods").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
    }

    // ---------- N+1 방지 ----------

    @Test
    void 목록_조회는_상품수와_무관하게_쿼리_5개_이하로_끝난다() throws Exception {
        // 목록(1) + count(1) + 배지(1) + 태그(1) + 별점(1) = 5. 비로그인이라 wished 공급자는 DB를 부르지 않는다
        // (WishedGoodsProvider 계약: viewerId가 null이면 조회 없이 빈 집합).
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        for (int i = 0; i < 20; i++) {
            상품_저장(brand, "C001001001", "상품" + i, 10000, 10000);
        }
        goodsRepository.flush();

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/v1/goods").param("size", "20"))
                .andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }

    // ---------- 헬퍼 ----------

    private Brand 브랜드_저장(String name) {
        return brandRepository.save(new Brand(name, null));
    }

    private void 카테고리_저장(String code, int depth) {
        categoryRepository.save(new Category(code, null, code, depth, 0));
    }

    private Goods 상품_저장(Brand brand, String categoryCode, String name, int listPrice, int salePrice) {
        Goods goods = new Goods(brand, categoryCode, name, "요약", "https://img.example/" + name + ".jpg",
                listPrice, salePrice);
        return goodsRepository.save(goods);
    }

    private List<String> 이름목록(MvcResult result) throws Exception {
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("content");
        return java.util.stream.StreamSupport.stream(content.spliterator(), false)
                .map(n -> n.get("name").asText())
                .toList();
    }

    private List<Long> goodsNo목록(MvcResult result) throws Exception {
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("content");
        return java.util.stream.StreamSupport.stream(content.spliterator(), false)
                .map(n -> n.get("goodsNo").asLong())
                .toList();
    }
}

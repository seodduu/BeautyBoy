package com.beautyboy.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GoodsDetailApiTest {

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
    GoodsQueryService goodsQueryService;

    // ---------- 기본 상세 ----------

    @Test
    void 존재하는_상품은_200과_기본정보를_반환한다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리3계층_저장();
        Goods goods = 상품_저장(brand, "C001001001", "토너", "요약", 10000, 8000);

        mockMvc.perform(get("/api/v1/goods/" + goods.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goodsNo").value(goods.getId()))
                .andExpect(jsonPath("$.data.brandName").value("브랜드1"))
                .andExpect(jsonPath("$.data.name").value("토너"))
                .andExpect(jsonPath("$.data.listPrice").value(10000))
                .andExpect(jsonPath("$.data.salePrice").value(8000))
                .andExpect(jsonPath("$.data.discountRate").value(20))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.rating").value(0.0))
                .andExpect(jsonPath("$.data.reviewCount").value(0))
                .andExpect(jsonPath("$.data.wished").value(false))
                .andExpect(jsonPath("$.data.todayDreamAvailable").value(false));
    }

    @Test
    void 옵션은_sort_order_순이고_재고0은_품절이다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리3계층_저장();
        Goods goods = 상품_저장(brand, "C001001001", "토너", "요약", 10000, 10000);
        goods.getOptions().add(new GoodsOption(goods, "두번째옵션", 0, 5, 1));
        goods.getOptions().add(new GoodsOption(goods, "첫번째옵션", 0, 0, 0));
        goodsRepository.save(goods);

        MvcResult result = mockMvc.perform(get("/api/v1/goods/" + goods.getId()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode options = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("options");
        assertThat(options.get(0).get("name").asText()).isEqualTo("첫번째옵션");
        assertThat(options.get(0).get("soldOut").asBoolean()).isTrue();
        assertThat(options.get(1).get("name").asText()).isEqualTo("두번째옵션");
        assertThat(options.get(1).get("soldOut").asBoolean()).isFalse();
    }

    @Test
    void sortOrder가_동률이면_id_오름차순으로_2차_정렬된다() throws Exception {
        // 대표 옵션 선택(GoodsService.대표_옵션_순서)이 sortOrder → id 순인데, 이 쿼리에
        // id 2차 정렬이 빠져 있으면 sortOrder 동률일 때 화면 순서와 대표 옵션이 어긋날 수 있었다.
        Brand brand = 브랜드_저장("브랜드1");
        카테고리3계층_저장();
        Goods goods = 상품_저장(brand, "C001001001", "토너", "요약", 10000, 10000);
        goods.getOptions().add(new GoodsOption(goods, "먼저추가", 0, 5, 0));
        goods.getOptions().add(new GoodsOption(goods, "나중추가", 0, 5, 0));
        Goods saved = goodsRepository.saveAndFlush(goods);

        List<GoodsOption> idAscending = saved.getOptions().stream()
                .sorted(java.util.Comparator.comparing(GoodsOption::getId))
                .toList();

        MvcResult result = mockMvc.perform(get("/api/v1/goods/" + goods.getId()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode options = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("options");
        assertThat(options.get(0).get("name").asText()).isEqualTo(idAscending.get(0).getName());
        assertThat(options.get(1).get("name").asText()).isEqualTo(idAscending.get(1).getName());
    }

    @Test
    void categoryPath는_depth_1부터_3까지_순서대로_3개다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        categoryRepository.save(new Category("C001", null, "스킨케어", 1, 0));
        categoryRepository.save(new Category("C001001", "C001", "토너/스킨", 2, 0));
        categoryRepository.save(new Category("C001001001", "C001001", "토너", 3, 0));
        Goods goods = 상품_저장(brand, "C001001001", "토너", "요약", 10000, 10000);

        MvcResult result = mockMvc.perform(get("/api/v1/goods/" + goods.getId()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode path = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("categoryPath");
        assertThat(path.get(0).asText()).isEqualTo("스킨케어");
        assertThat(path.get(1).asText()).isEqualTo("토너/스킨");
        assertThat(path.get(2).asText()).isEqualTo("토너");
        assertThat(path.size()).isEqualTo(3);
    }

    @Test
    void 없는_goodsNo는_404_GOODS_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/goods/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"));
    }

    @Test
    void HIDDEN_상품_상세는_404다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리3계층_저장();
        Goods goods = 상품_저장(brand, "C001001001", "숨김상품", "요약", 10000, 10000);
        goods.hide();
        goodsRepository.save(goods);

        mockMvc.perform(get("/api/v1/goods/" + goods.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"));
    }

    // ---------- 설명(지연 로딩) ----------

    @Test
    void description_엔드포인트는_본문을_반환하고_기본상세엔_description_필드가_없다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리3계층_저장();
        Goods goods = new Goods(brand, "C001001001", "토너", "요약", "https://img.example/토너.jpg", 10000, 10000);
        setDescription(goods, "이 토너는 무알콜입니다.");
        goodsRepository.save(goods);

        mockMvc.perform(get("/api/v1/goods/" + goods.getId() + "/description"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goodsNo").value(goods.getId()))
                .andExpect(jsonPath("$.data.description").value("이 토너는 무알콜입니다."));

        mockMvc.perform(get("/api/v1/goods/" + goods.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").doesNotExist());
    }

    @Test
    void 없는_goodsNo의_description은_404다() throws Exception {
        mockMvc.perform(get("/api/v1/goods/999999/description"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"));
    }

    @Test
    void HIDDEN_상품의_description도_404다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리3계층_저장();
        Goods goods = 상품_저장(brand, "C001001001", "숨김상품", "요약", 10000, 10000);
        goods.hide();
        goodsRepository.save(goods);

        mockMvc.perform(get("/api/v1/goods/" + goods.getId() + "/description"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"));
    }

    // ---------- 추천 ----------

    @Test
    void recommended는_같은_카테고리만_자기제외_최대8건_view_count_내림차순이다() throws Exception {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리_저장("C001001001", 3);
        카테고리_저장("C001002001", 3);
        Goods target = 상품_저장(brand, "C001001001", "타깃상품", "요약", 10000, 10000);

        for (int i = 0; i < 9; i++) {
            Goods g = 상품_저장(brand, "C001001001", "같은카테고리" + i, "요약", 10000, 10000);
            g.increaseViewCount(100 - i);
            goodsRepository.save(g);
        }
        상품_저장(brand, "C001002001", "다른카테고리", "요약", 10000, 10000);

        MvcResult result = mockMvc.perform(get("/api/v1/goods/" + target.getId() + "/recommended"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(content.size()).isEqualTo(8);
        for (JsonNode node : content) {
            assertThat(node.get("name").asText()).doesNotContain("타깃상품", "다른카테고리");
        }
        assertThat(content.get(0).get("name").asText()).isEqualTo("같은카테고리0");
    }

    // ---------- GoodsQueryService.exists ----------

    @Test
    void exists는_존재하는_노출상품이면_true다() {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리3계층_저장();
        Goods goods = 상품_저장(brand, "C001001001", "토너", "요약", 10000, 10000);

        assertThat(goodsQueryService.exists(goods.getId())).isTrue();
    }

    @Test
    void exists는_없는_goodsNo면_false다() {
        assertThat(goodsQueryService.exists(999999L)).isFalse();
    }

    @Test
    void exists는_HIDDEN_상품이면_false다() {
        Brand brand = 브랜드_저장("브랜드1");
        카테고리3계층_저장();
        Goods goods = 상품_저장(brand, "C001001001", "숨김상품", "요약", 10000, 10000);
        goods.hide();
        goodsRepository.save(goods);

        assertThat(goodsQueryService.exists(goods.getId())).isFalse();
    }

    // ---------- 헬퍼 ----------

    private Brand 브랜드_저장(String name) {
        return brandRepository.save(new Brand(name, null));
    }

    private void 카테고리_저장(String code, int depth) {
        if (categoryRepository.existsById(code)) {
            return;
        }
        categoryRepository.save(new Category(code, null, code, depth, 0));
    }

    private void 카테고리3계층_저장() {
        if (categoryRepository.existsById("C001")) {
            return;
        }
        categoryRepository.save(new Category("C001", null, "스킨케어", 1, 0));
        categoryRepository.save(new Category("C001001", "C001", "토너/스킨", 2, 0));
        categoryRepository.save(new Category("C001001001", "C001001", "토너", 3, 0));
    }

    private Goods 상품_저장(Brand brand, String categoryCode, String name, String summary, int listPrice, int salePrice) {
        Goods goods = new Goods(brand, categoryCode, name, summary, "https://img.example/" + name + ".jpg",
                listPrice, salePrice);
        return goodsRepository.save(goods);
    }

    private void setDescription(Goods goods, String description) {
        try {
            var field = Goods.class.getDeclaredField("description");
            field.setAccessible(true);
            field.set(goods, description);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

package com.beautyboy.ingredient;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Category;
import com.beautyboy.catalog.CategoryRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class GoodsIngredientApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    IngredientRepository ingredientRepository;
    @Autowired
    GoodsIngredientRepository goodsIngredientRepository;
    @Autowired
    IngredientRuleRepository ingredientRuleRepository;
    @Autowired
    GoodsIngredientQueryService goodsIngredientQueryService;
    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void 성분_3개_매핑된_상품은_is_key가_앞이고_최대자극도를_반환한다() throws Exception {
        Goods goods = 상품_저장();

        Ingredient retinol = 성분_저장("레티놀", "RETINOID", 5, 2, "강한 자극의 항노화 성분");
        Ingredient niacinamide = 성분_저장("나이아신아마이드", "NIACINAMIDE", 1, 0, "순한 미백 성분");
        Ingredient ha = 성분_저장("히알루론산", "HYALURONIC", 1, 0, "보습 성분");

        goodsIngredientRepository.save(new GoodsIngredient(goods.getId(), niacinamide.getId(), false, 0));
        goodsIngredientRepository.save(new GoodsIngredient(goods.getId(), retinol.getId(), true, 1));
        goodsIngredientRepository.save(new GoodsIngredient(goods.getId(), ha.getId(), false, 2));

        mockMvc.perform(get("/api/v1/goods/" + goods.getId() + "/ingredients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goodsNo").value(goods.getId()))
                .andExpect(jsonPath("$.data.ingredients.length()").value(3))
                .andExpect(jsonPath("$.data.ingredients[0].name").value("레티놀"))
                .andExpect(jsonPath("$.data.ingredients[0].key").value(true))
                .andExpect(jsonPath("$.data.ingredients[1].name").value("나이아신아마이드"))
                .andExpect(jsonPath("$.data.ingredients[2].name").value("히알루론산"))
                .andExpect(jsonPath("$.data.maxIrritation").value(5))
                .andExpect(jsonPath("$.data.maxComedogenic").value(2));
    }

    @Test
    void 성분이_없는_상품은_200과_빈배열을_반환한다() throws Exception {
        Goods goods = 상품_저장();

        mockMvc.perform(get("/api/v1/goods/" + goods.getId() + "/ingredients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goodsNo").value(goods.getId()))
                .andExpect(jsonPath("$.data.ingredients.length()").value(0))
                .andExpect(jsonPath("$.data.maxIrritation").value(0))
                .andExpect(jsonPath("$.data.maxComedogenic").value(0));
    }

    @Test
    void 없는_상품번호는_404_GOODS_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/goods/999999/ingredients"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"));
    }

    @Test
    void HIDDEN_상품은_404를_반환한다() throws Exception {
        Goods goods = 상품_저장();
        goods.hide();
        goodsRepository.save(goods);

        mockMvc.perform(get("/api/v1/goods/" + goods.getId() + "/ingredients"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"));
    }

    @Test
    void findCategoriesByGoodsIds는_상품_여러개를_1쿼리로_묶어_반환한다() {
        Goods g1 = 상품_저장();
        Goods g2 = 상품_저장();
        Goods g3 = 상품_저장();

        Ingredient retinol = 성분_저장("레티놀2", "RETINOID", 5, 2, "요약");
        Ingredient niacinamide = 성분_저장("나이아신아마이드2", "NIACINAMIDE", 1, 0, "요약");
        Ingredient ha = 성분_저장("히알루론산2", "HYALURONIC", 1, 0, "요약");

        goodsIngredientRepository.save(new GoodsIngredient(g1.getId(), retinol.getId(), true, 0));
        goodsIngredientRepository.save(new GoodsIngredient(g2.getId(), niacinamide.getId(), true, 0));
        goodsIngredientRepository.save(new GoodsIngredient(g3.getId(), ha.getId(), true, 0));
        goodsIngredientRepository.flush();

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        Map<Long, Set<String>> result = goodsIngredientQueryService
                .findCategoriesByGoodsIds(List.of(g1.getId(), g2.getId(), g3.getId()));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(result.get(g1.getId())).containsExactly("RETINOID");
        assertThat(result.get(g2.getId())).containsExactly("NIACINAMIDE");
        assertThat(result.get(g3.getId())).containsExactly("HYALURONIC");
    }

    @Test
    void ingredient_rule은_category_a_category_b_규약위반_입력도_정규화되어_조회된다() {
        // 사전순 위반: SALICYLIC > BHA 이므로 (SALICYLIC, BHA)로 저장 시도해도 (BHA, SALICYLIC)으로 정규화되어야 한다
        ingredientRuleRepository.saveNormalized(
                new IngredientRule(null, "SALICYLIC", "BHA", "CAUTION", "함께 쓰면 자극이 커질 수 있어요"));

        IngredientRule found = ingredientRuleRepository.findNormalized("BHA", "SALICYLIC").orElseThrow();
        assertThat(found.getCategoryA()).isEqualTo("BHA");
        assertThat(found.getCategoryB()).isEqualTo("SALICYLIC");

        IngredientRule foundReversed = ingredientRuleRepository.findNormalized("SALICYLIC", "BHA").orElseThrow();
        assertThat(foundReversed.getCategoryA()).isEqualTo("BHA");
        assertThat(foundReversed.getCategoryB()).isEqualTo("SALICYLIC");
    }

    // ---------- 헬퍼 ----------

    private int goodsSeq = 0;

    private Goods 상품_저장() {
        goodsSeq++;
        Brand brand = brandRepository.save(new Brand("브랜드" + goodsSeq, null));
        categoryRepository.save(new Category("C001001001", null, "카테고리", 3, 0));
        Goods goods = new Goods(brand, "C001001001", "상품" + goodsSeq, "요약", "https://img.example/x.jpg", 10000, 10000);
        return goodsRepository.save(goods);
    }

    private Ingredient 성분_저장(String name, String category, int irritationLevel, int comedogenic, String summary) {
        return ingredientRepository.save(new Ingredient(name, category, irritationLevel, comedogenic, summary));
    }
}

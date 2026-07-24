package com.beautyboy.ingredient;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Category;
import com.beautyboy.catalog.CategoryRepository;
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
class AssessmentApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired BrandRepository brandRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired GoodsRepository goodsRepository;
    @Autowired IngredientRepository ingredientRepository;
    @Autowired GoodsIngredientRepository goodsIngredientRepository;
    @Autowired IngredientRegFlagRepository regFlagRepository;

    @Test
    void 씻어내는_클렌저_판정_응답_형태() throws Exception {
        // 클렌징(C002001, rinse-off) + 살리실산(각질산). N=1 → 보정 -1 → NO_CONCERN
        Goods goods = 상품_저장("C002001");
        Ingredient sal = 성분_저장("살리실산", "salicylic acid");
        goodsIngredientRepository.save(new GoodsIngredient(goods.getId(), sal.getId(), true, 0));
        regFlagRepository.save(new IngredientRegFlag(
                "salicylic acid", "살리실릭애씨드", null, "EXFOLIANT_ACID", "INTERNAL_ACID", "BHA"));

        mockMvc.perform(get("/api/v1/goods/" + goods.getId() + "/assessment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goodsNo").value(goods.getId()))
                .andExpect(jsonPath("$.data.verdictCode").value("NO_CONCERN"))
                .andExpect(jsonPath("$.data.verdictText").value("걱정 성분이 거의 없어요"))
                .andExpect(jsonPath("$.data.rinseOff").value(true))
                .andExpect(jsonPath("$.data.checkCount").value(1))
                .andExpect(jsonPath("$.data.flagged").isArray())
                .andExpect(jsonPath("$.data.flagged[0].axis").value("CHECK"));
    }

    @Test
    void leaveon_착향제2개는_대체로무난() throws Exception {
        Goods goods = 상품_저장("C001002"); // 세럼 = leave-on
        Ingredient limo = 성분_저장("리모넨", "limonene");
        Ingredient lina = 성분_저장("리날룰", "linalool");
        goodsIngredientRepository.save(new GoodsIngredient(goods.getId(), limo.getId(), true, 0));
        goodsIngredientRepository.save(new GoodsIngredient(goods.getId(), lina.getId(), false, 1));
        regFlagRepository.save(new IngredientRegFlag("limonene", "리모넨", null, "ALLERGEN", "MFDS_ALLERGEN_25", "25종"));
        regFlagRepository.save(new IngredientRegFlag("linalool", "리날룰", null, "ALLERGEN", "MFDS_ALLERGEN_25", "25종"));

        mockMvc.perform(get("/api/v1/goods/" + goods.getId() + "/assessment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdictCode").value("MOSTLY_FINE"))
                .andExpect(jsonPath("$.data.checkCount").value(2))
                .andExpect(jsonPath("$.data.rinseOff").value(false));
    }

    @Test
    void 없는_상품은_404_GOODS_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/api/v1/goods/999999/assessment"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"));
    }

    private int seq = 0;

    private Goods 상품_저장(String categoryCode) {
        seq++;
        Brand brand = brandRepository.save(new Brand("브랜드" + seq, null));
        if (categoryRepository.findById(categoryCode).isEmpty()) {
            categoryRepository.save(new Category(categoryCode, null, "카테고리", 3, 0));
        }
        Goods goods = new Goods(brand, categoryCode, "상품" + seq, "요약", "https://img.example/x.jpg", 10000, 10000);
        return goodsRepository.save(goods);
    }

    private Ingredient 성분_저장(String name, String inci) {
        Ingredient ingredient = new Ingredient(name, "OTHER", 1, 0, "요약");
        ingredient.setInciName(inci);
        return ingredientRepository.save(ingredient);
    }
}

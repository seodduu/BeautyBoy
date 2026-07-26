package com.beautyboy.compat;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Category;
import com.beautyboy.catalog.CategoryRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.ingredient.GoodsIngredient;
import com.beautyboy.ingredient.GoodsIngredientRepository;
import com.beautyboy.ingredient.Ingredient;
import com.beautyboy.ingredient.IngredientRepository;
import com.beautyboy.ingredient.IngredientRule;
import com.beautyboy.ingredient.IngredientRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V12 시드에 의존하지 않고 상품·성분·규칙을 자가 주입한다(터미널 병렬 안전 원칙).
 * base: AHA is_key / candA: RETINOID / candB: NIACINAMIDE / candC: VITAMIN_C / candD: 성분 없음
 * 규칙: (AHA,RETINOID)=CONFLICT, (AHA,VITAMIN_C)=CAUTION  ※ (AHA,NIACINAMIDE) 규칙 없음
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompatQueryServiceTest {

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
    CompatQueryService compatQueryService;

    private int goodsSeq = 0;

    private Goods 상품_저장() {
        goodsSeq++;
        Brand brand = brandRepository.save(new Brand("브랜드" + goodsSeq, null));
        categoryRepository.save(new Category("C001001001", null, "카테고리", 3, 0));
        Goods goods = new Goods(brand, "C001001001", "상품" + goodsSeq, "요약", "https://img.example/x.jpg", 10000, 10000);
        return goodsRepository.save(goods);
    }

    private Ingredient 성분_저장(String name, String category) {
        return ingredientRepository.save(new Ingredient(name, category, 3, 1, "요약"));
    }

    @Test
    void 기준상품과_후보들의_최악_판정을_배치로_돌려준다() {
        Goods base = 상품_저장();
        Goods candA = 상품_저장();
        Goods candB = 상품_저장();
        Goods candC = 상품_저장();

        Ingredient aha = 성분_저장("AHA성분", "AHA");
        Ingredient retinoid = 성분_저장("레티노이드성분", "RETINOID");
        Ingredient niacinamide = 성분_저장("나이아신아마이드성분", "NIACINAMIDE");
        Ingredient vitaminC = 성분_저장("비타민씨성분", "VITAMIN_C");

        goodsIngredientRepository.save(new GoodsIngredient(base.getId(), aha.getId(), true, 0));
        goodsIngredientRepository.save(new GoodsIngredient(candA.getId(), retinoid.getId(), true, 0));
        goodsIngredientRepository.save(new GoodsIngredient(candB.getId(), niacinamide.getId(), true, 0));
        goodsIngredientRepository.save(new GoodsIngredient(candC.getId(), vitaminC.getId(), true, 0));

        ingredientRuleRepository.saveNormalized(
                new IngredientRule(null, "AHA", "RETINOID", "CONFLICT", "자극 중첩"));
        ingredientRuleRepository.saveNormalized(
                new IngredientRule(null, "AHA", "VITAMIN_C", "CAUTION", "산성도 차이 주의"));

        Map<Long, String> verdicts = compatQueryService.worstVerdicts(
                base.getId(), List.of(candA.getId(), candB.getId(), candC.getId()));

        assertThat(verdicts.get(candA.getId())).isEqualTo("CONFLICT");
        assertThat(verdicts.get(candB.getId())).isEqualTo("OK");
        assertThat(verdicts.get(candC.getId())).isEqualTo("CAUTION");
    }

    @Test
    void 성분이_없는_후보는_OK() {
        Goods base = 상품_저장();
        Goods candD = 상품_저장();

        Ingredient aha = 성분_저장("AHA성분", "AHA");
        goodsIngredientRepository.save(new GoodsIngredient(base.getId(), aha.getId(), true, 0));

        Map<Long, String> verdicts = compatQueryService.worstVerdicts(base.getId(), List.of(candD.getId()));

        assertThat(verdicts.get(candD.getId())).isEqualTo("OK");
    }

    @Test
    void 빈_후보면_빈_맵() {
        Goods base = 상품_저장();

        Map<Long, String> verdicts = compatQueryService.worstVerdicts(base.getId(), List.of());

        assertThat(verdicts).isEmpty();
    }
}

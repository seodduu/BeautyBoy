package com.beautyboy.ingredient;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Category;
import com.beautyboy.catalog.CategoryRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IngredientRegFlagRepositoryTest {

    @Autowired BrandRepository brandRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired GoodsRepository goodsRepository;
    @Autowired IngredientRepository ingredientRepository;
    @Autowired GoodsIngredientRepository goodsIngredientRepository;
    @Autowired IngredientRegFlagRepository regFlagRepository;

    @Test
    void 제품_성분과_플래그를_LEFT_JOIN으로_모은다() {
        Goods goods = 상품_저장();
        Ingredient sal = 성분_저장("살리실산", "salicylic acid", "BHA");
        Ingredient cetyl = 성분_저장("세틸알코올", "cetyl alcohol", "ALCOHOL");
        goodsIngredientRepository.save(new GoodsIngredient(goods.getId(), sal.getId(), true, 0));
        goodsIngredientRepository.save(new GoodsIngredient(goods.getId(), cetyl.getId(), false, 1));

        // 살리실산은 두 플래그(각질산 + 한도), 세틸알코올은 플래그 없음
        regFlagRepository.save(new IngredientRegFlag(
                "salicylic acid", "살리실릭애씨드", null, "EXFOLIANT_ACID", "INTERNAL_ACID", "BHA"));
        regFlagRepository.save(new IngredientRegFlag(
                "salicylic acid", "살리실릭애씨드", null, "LIMIT", "MFDS_RESTRICT", "* 배합한도 : 0.5%"));

        List<Object[]> rows = regFlagRepository.findFlagRowsByGoodsId(goods.getId());

        // 행: [id, name, inci, summary, flag_type(4), source_ref(5), sort_order(6)]
        Set<String> flags = rows.stream()
                .filter(r -> r[4] != null)
                .map(r -> (String) r[4])
                .collect(Collectors.toSet());
        assertThat(flags).containsExactlyInAnyOrder("EXFOLIANT_ACID", "LIMIT");

        // 세틸알코올(무플래그)도 LEFT JOIN으로 행에 존재한다
        boolean cetylPresentWithoutFlag = rows.stream()
                .anyMatch(r -> ((Number) r[0]).longValue() == cetyl.getId() && r[4] == null);
        assertThat(cetylPresentWithoutFlag).isTrue();
    }

    private int seq = 0;

    private Goods 상품_저장() {
        seq++;
        Brand brand = brandRepository.save(new Brand("브랜드" + seq, null));
        if (categoryRepository.findById("C001001001").isEmpty()) {
            categoryRepository.save(new Category("C001001001", null, "카테고리", 3, 0));
        }
        Goods goods = new Goods(brand, "C001001001", "상품" + seq, "요약", "https://img.example/x.jpg", 10000, 10000);
        return goodsRepository.save(goods);
    }

    private Ingredient 성분_저장(String name, String inci, String category) {
        Ingredient ingredient = new Ingredient(name, category, 1, 0, "요약");
        ingredient.setInciName(inci);
        return ingredientRepository.save(ingredient);
    }
}

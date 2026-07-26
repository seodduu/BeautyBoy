package com.beautyboy.routine;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.catalog.GoodsTag;
import com.beautyboy.catalog.GoodsTagRepository;
import com.beautyboy.catalog.Tag;
import com.beautyboy.catalog.TagRepository;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.ingredient.GoodsIngredient;
import com.beautyboy.ingredient.GoodsIngredientRepository;
import com.beautyboy.ingredient.Ingredient;
import com.beautyboy.ingredient.IngredientRepository;
import com.beautyboy.ingredient.IngredientRule;
import com.beautyboy.ingredient.IngredientRuleRepository;
import com.beautyboy.routine.dto.NextStepBlock;
import com.beautyboy.routine.dto.NextStepResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 흐름 추천 핵심 — 규칙 매칭 → 폴백 사다리 → 궁합 게이트.
 * 픽스처는 전부 자가주입한다(V74/V75 시드 의존 금지 — 시드 검증은 Task 5).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NextStepServiceTest {

    @Autowired NextStepService nextStepService;
    @Autowired RoutineFlowRuleRepository ruleRepository;
    @Autowired BrandRepository brandRepository;
    @Autowired GoodsRepository goodsRepository;
    @Autowired TagRepository tagRepository;
    @Autowired GoodsTagRepository goodsTagRepository;
    @Autowired IngredientRepository ingredientRepository;
    @Autowired GoodsIngredientRepository goodsIngredientRepository;
    @Autowired IngredientRuleRepository ingredientRuleRepository;

    @Test
    void 각질_토너는_BUFFER가_NEXT_STEP보다_우선한다() {
        Long 각질토너 = 각질토너_저장();
        규칙_저장("C001001", "exfoliate", "C001002", "soothe", "BUFFER", "각질 케어 다음엔 진정으로 완충", 10);
        규칙_저장("C001001", null, "C001002", null, "NEXT_STEP", "다음 단계로", 20);
        Long soothe세럼 = 상품_저장("C001002001", 300);
        태그_부여(soothe세럼, "soothe");

        NextStepResponse res = nextStepService.find(각질토너, null);

        assertThat(res.blocks()).hasSize(1);
        assertThat(res.blocks().get(0).edgeKind()).isEqualTo("BUFFER");
        assertThat(res.blocks().get(0).reason()).isEqualTo("각질 케어 다음엔 진정으로 완충");
    }

    @Test
    void 태그_일치가_부족하면_같은_카테고리_인기순으로_채운다() {
        Long 각질토너 = 각질토너_저장();
        규칙_저장("C001001", "exfoliate", "C001002", "soothe", "BUFFER", "각질 케어 다음엔 진정으로 완충", 10);
        Long soothe세럼 = 상품_저장("C001002001", 50);
        태그_부여(soothe세럼, "soothe");
        Long 인기세럼1 = 상품_저장("C001002001", 300);
        Long 인기세럼2 = 상품_저장("C001002001", 200);

        List<GoodsListItem> items = nextStepService.find(각질토너, null).blocks().get(0).items();

        assertThat(items).extracting(GoodsListItem::goodsNo)
                .containsExactly(soothe세럼, 인기세럼1, 인기세럼2);
    }

    @Test
    void CONFLICT_후보는_게이트에서_제거된다() {
        Long 각질토너 = 각질토너_저장();
        성분_부여(각질토너, "AHA성분", "AHA");
        규칙_저장("C001001", "exfoliate", "C001002", null, "BUFFER", "각질 케어 다음엔 진정으로 완충", 10);
        Long 레티노이드세럼 = 상품_저장("C001002001", 300);
        성분_부여(레티노이드세럼, "레티노이드성분", "RETINOID");
        Long 안전세럼 = 상품_저장("C001002001", 200);
        ingredientRuleRepository.saveNormalized(new IngredientRule(null, "AHA", "RETINOID", "CONFLICT", "자극 중첩"));

        List<GoodsListItem> items = nextStepService.find(각질토너, null).blocks().get(0).items();

        assertThat(items).extracting(GoodsListItem::goodsNo).doesNotContain(레티노이드세럼);
        assertThat(items).extracting(GoodsListItem::goodsNo).containsExactly(안전세럼);
    }

    @Test
    void 후보가_모두_제거되면_블록을_내지_않는다() {
        Long 각질토너 = 각질토너_저장();
        성분_부여(각질토너, "AHA성분", "AHA");
        규칙_저장("C001001", "exfoliate", "C001002", null, "BUFFER", "각질 케어 다음엔 진정으로 완충", 10);
        Long 레티노이드세럼 = 상품_저장("C001002001", 300);
        성분_부여(레티노이드세럼, "레티노이드성분", "RETINOID");
        ingredientRuleRepository.saveNormalized(new IngredientRule(null, "AHA", "RETINOID", "CONFLICT", "자극 중첩"));

        assertThat(nextStepService.find(각질토너, null).blocks()).isEmpty();
    }

    @Test
    void 선크림은_순방향과_PAIRED_REMOVAL_두_블록() {
        Long 선크림 = 상품_저장("C004001001", 500);
        태그_부여(선크림, "uv");
        // PAIRED_REMOVAL의 priority가 더 낮아도(=우선해도) 순방향 블록이 앞에 온다.
        규칙_저장("C004001", "uv", "C002002", "cleanse", "PAIRED_REMOVAL", "자외선차단제는 오일로 지워야 남지 않아요", 10);
        규칙_저장("C004001", "uv", "C001003", null, "NEXT_STEP", "진정으로 마무리", 20);
        상품_저장("C002002001", 300);
        상품_저장("C001003001", 300);

        NextStepResponse res = nextStepService.find(선크림, null);

        assertThat(res.blocks()).extracting(NextStepBlock::edgeKind)
                .containsExactly("NEXT_STEP", "PAIRED_REMOVAL");
    }

    @Test
    void 규칙이_없는_상품은_빈_blocks() {
        Long 면도날카트리지 = 상품_저장("C005001001", 100);
        규칙_저장("C001001", "exfoliate", "C001002", "soothe", "BUFFER", "각질 케어 다음엔 진정으로 완충", 10);

        assertThat(nextStepService.find(면도날카트리지, null).blocks()).isEmpty();
    }

    @Test
    void 없는_상품은_GOODS_NOT_FOUND() {
        assertThatThrownBy(() -> nextStepService.find(999999L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GOODS_NOT_FOUND);
    }

    // ---------- 픽스처 ----------

    private int seq = 0;

    private Long 각질토너_저장() {
        Long 토너 = 상품_저장("C001001001", 400);
        태그_부여(토너, "exfoliate");
        return 토너;
    }

    private Long 상품_저장(String categoryCode, int viewCount) {
        seq++;
        Brand brand = brandRepository.save(new Brand("브랜드" + seq + "_" + System.nanoTime(), null));
        Goods goods = new Goods(brand, categoryCode, "상품" + seq, "요약", "https://img.example/x.jpg", 10000, 10000);
        goods.increaseViewCount(viewCount);
        return goodsRepository.save(goods).getId();
    }

    private void 태그_부여(Long goodsNo, String slug) {
        Tag tag = tagRepository.save(new Tag(slug + seq, "EFFECT", slug, 0));
        goodsTagRepository.save(new GoodsTag(goodsNo, tag.getId(), null, 0));
    }

    private void 성분_부여(Long goodsNo, String name, String category) {
        Ingredient ingredient = ingredientRepository.save(new Ingredient(name, category, 3, 1, "요약"));
        goodsIngredientRepository.save(new GoodsIngredient(goodsNo, ingredient.getId(), true, 0));
    }

    private void 규칙_저장(String fromCategoryCode, String fromTagSlug, String toCategoryCode, String toTagSlug,
                       String edgeKind, String reason, int priority) {
        ruleRepository.save(new RoutineFlowRule(null, fromCategoryCode, fromTagSlug, toCategoryCode, toTagSlug,
                edgeKind, reason, priority));
    }
}

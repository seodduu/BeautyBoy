package com.beautyboy.compat;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.compat.dto.CompatCheckRequest;
import com.beautyboy.compat.dto.CompatCheckResponse;
import com.beautyboy.compat.dto.CompatFinding;
import com.beautyboy.ingredient.GoodsIngredientQueryService;
import com.beautyboy.ingredient.IngredientRuleQueryService;
import com.beautyboy.ingredient.IngredientRuleQueryService.RuleVerdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CompatServiceTest {

    @Mock
    GoodsIngredientQueryService goodsIngredientQueryService;
    @Mock
    IngredientRuleQueryService ruleQueryService;
    @InjectMocks
    CompatService compatService;

    @Test
    void 레티노이드와_AHA가_한_선택에_있으면_CONFLICT를_낸다() {
        given(goodsIngredientQueryService.findCategoriesByGoodsIds(List.of(1L, 2L)))
                .willReturn(Map.of(1L, Set.of("RETINOID"), 2L, Set.of("AHA")));
        given(ruleQueryService.findNormalized("AHA", "RETINOID"))
                .willReturn(Optional.of(new RuleVerdict("AHA", "RETINOID", "CONFLICT", "자극 중첩")));

        CompatCheckResponse r = compatService.check(new CompatCheckRequest(List.of(1L, 2L)));

        assertThat(r.overall()).isEqualTo("CONFLICT");
        assertThat(r.findings()).singleElement().satisfies(f -> {
            assertThat(f.verdict()).isEqualTo("CONFLICT");
            assertThat(f.categoryA()).isEqualTo("AHA");
            assertThat(f.categoryB()).isEqualTo("레티노이드");
            assertThat(f.reason()).isEqualTo("자극 중첩");
            assertThat(f.goodsNos()).containsExactly(1L, 2L);
        });
    }

    @Test
    void 규칙_없는_조합은_OK다() {
        given(goodsIngredientQueryService.findCategoriesByGoodsIds(List.of(1L, 2L)))
                .willReturn(Map.of(1L, Set.of("HYALURONIC"), 2L, Set.of("CERAMIDE")));
        given(ruleQueryService.findNormalized("CERAMIDE", "HYALURONIC"))
                .willReturn(Optional.empty());

        CompatCheckResponse r = compatService.check(new CompatCheckRequest(List.of(1L, 2L)));

        assertThat(r.overall()).isEqualTo("OK");
        assertThat(r.findings()).isEmpty();
    }

    @Test
    void 빈_선택은_COMPAT_EMPTY_SELECTION을_던진다() {
        assertThatThrownBy(() -> compatService.check(new CompatCheckRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPAT_EMPTY_SELECTION));
    }

    @Test
    void null_선택은_COMPAT_EMPTY_SELECTION을_던진다() {
        assertThatThrownBy(() -> compatService.check(new CompatCheckRequest(null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPAT_EMPTY_SELECTION));
    }

    @Test
    void 한_제품이_두_분류를_다_가지면_자기충돌도_잡는다() {
        given(goodsIngredientQueryService.findCategoriesByGoodsIds(List.of(1L)))
                .willReturn(Map.of(1L, Set.of("AHA", "RETINOID")));
        given(ruleQueryService.findNormalized("AHA", "RETINOID"))
                .willReturn(Optional.of(new RuleVerdict("AHA", "RETINOID", "CONFLICT", "자극 중첩")));

        CompatCheckResponse r = compatService.check(new CompatCheckRequest(List.of(1L)));

        assertThat(r.overall()).isEqualTo("CONFLICT");
        assertThat(r.findings()).singleElement().satisfies(f -> {
            assertThat(f.verdict()).isEqualTo("CONFLICT");
            assertThat(f.goodsNos()).containsExactly(1L);
        });
    }

    @Test
    void findings는_심각도_내림차순_동률이면_분류사전순으로_정렬된다() {
        // 상품1: AHA, 상품2: RETINOID, 상품3: VITAMINC, 상품4: BHA
        // 규칙: AHA-RETINOID = CONFLICT, RETINOID-VITAMINC = SYNERGY, AHA-BHA = CAUTION
        given(goodsIngredientQueryService.findCategoriesByGoodsIds(List.of(1L, 2L, 3L, 4L)))
                .willReturn(Map.of(
                        1L, Set.of("AHA"),
                        2L, Set.of("RETINOID"),
                        3L, Set.of("VITAMINC"),
                        4L, Set.of("BHA")));
        given(ruleQueryService.findNormalized("AHA", "BHA"))
                .willReturn(Optional.of(new RuleVerdict("AHA", "BHA", "CAUTION", "각질 이중 사용 주의")));
        given(ruleQueryService.findNormalized("AHA", "RETINOID"))
                .willReturn(Optional.of(new RuleVerdict("AHA", "RETINOID", "CONFLICT", "자극 중첩")));
        given(ruleQueryService.findNormalized("AHA", "VITAMINC"))
                .willReturn(Optional.empty());
        given(ruleQueryService.findNormalized("BHA", "RETINOID"))
                .willReturn(Optional.empty());
        given(ruleQueryService.findNormalized("BHA", "VITAMINC"))
                .willReturn(Optional.empty());
        given(ruleQueryService.findNormalized("RETINOID", "VITAMINC"))
                .willReturn(Optional.of(new RuleVerdict("RETINOID", "VITAMINC", "SYNERGY", "항산화 시너지")));

        CompatCheckResponse r = compatService.check(new CompatCheckRequest(List.of(1L, 2L, 3L, 4L)));

        assertThat(r.overall()).isEqualTo("CONFLICT");
        assertThat(r.findings()).extracting(f -> f.verdict())
                .containsExactly("CONFLICT", "CAUTION", "SYNERGY");
        assertThat(r.findings()).extracting(f -> f.categoryA())
                .containsExactly("AHA", "AHA", "레티노이드");
        assertThat(r.findings().get(0).goodsNos()).containsExactly(1L, 2L);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("finding의 categoryA/B는 코드가 아니라 한글 표시명으로 내려간다")
    void 카테고리_표시명_변환() {
        // VITAMIN_C × NIACINAMIDE 규칙이 걸리는 카트를 구성한다.
        // 정규화(사전순)로 ca=NIACINAMIDE, cb=VITAMIN_C — 라벨도 그 순서로 내려간다.
        given(goodsIngredientQueryService.findCategoriesByGoodsIds(List.of(1L, 2L)))
                .willReturn(Map.of(1L, Set.of("VITAMIN_C"), 2L, Set.of("NIACINAMIDE")));
        given(ruleQueryService.findNormalized("NIACINAMIDE", "VITAMIN_C"))
                .willReturn(Optional.of(new RuleVerdict(
                        "NIACINAMIDE", "VITAMIN_C", "CAUTION", "동시 사용 주의")));

        CompatCheckResponse response = compatService.check(new CompatCheckRequest(List.of(1L, 2L)));

        CompatFinding finding = response.findings().get(0);
        assertThat(finding.categoryA()).isEqualTo("나이아신아마이드");   // "NIACINAMIDE"면 실패
        assertThat(finding.categoryB()).isEqualTo("비타민C");            // "VITAMIN_C"면 실패
    }

    @Test
    void 동률_CONFLICT는_categoryA_그다음_categoryB_사전순으로_정렬된다() {
        // AHA-RETINOID CONFLICT, AHA-BHA CONFLICT -> categoryA 동률(AHA)이면 categoryB 순 (BHA < RETINOID)
        given(goodsIngredientQueryService.findCategoriesByGoodsIds(List.of(1L, 2L, 3L)))
                .willReturn(Map.of(1L, Set.of("AHA"), 2L, Set.of("RETINOID"), 3L, Set.of("BHA")));
        given(ruleQueryService.findNormalized("AHA", "BHA"))
                .willReturn(Optional.of(new RuleVerdict("AHA", "BHA", "CONFLICT", "b")));
        given(ruleQueryService.findNormalized("AHA", "RETINOID"))
                .willReturn(Optional.of(new RuleVerdict("AHA", "RETINOID", "CONFLICT", "r")));
        given(ruleQueryService.findNormalized("BHA", "RETINOID"))
                .willReturn(Optional.empty());

        CompatCheckResponse r = compatService.check(new CompatCheckRequest(List.of(1L, 2L, 3L)));

        assertThat(r.findings()).extracting(f -> f.categoryB())
                .containsExactly("BHA", "레티노이드");
    }
}

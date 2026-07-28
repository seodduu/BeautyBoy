package com.beautyboy.ingredient;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngredientCategoryLabelsTest {

    @Test
    @DisplayName("14종 코드 전부가 표시명으로 변환된다 — 코드가 그대로 남는 카테고리가 없다")
    void 전체_코드_매핑() {
        assertThat(IngredientCategoryLabels.labelOf("RETINOID")).isEqualTo("레티노이드");
        assertThat(IngredientCategoryLabels.labelOf("VITAMIN_C")).isEqualTo("비타민C");
        assertThat(IngredientCategoryLabels.labelOf("NIACINAMIDE")).isEqualTo("나이아신아마이드");
        assertThat(IngredientCategoryLabels.labelOf("HYALURONIC")).isEqualTo("히알루론산");
        assertThat(IngredientCategoryLabels.labelOf("CERAMIDE")).isEqualTo("세라마이드");
        assertThat(IngredientCategoryLabels.labelOf("PEPTIDE")).isEqualTo("펩타이드");
        assertThat(IngredientCategoryLabels.labelOf("CENTELLA")).isEqualTo("센텔라");
        assertThat(IngredientCategoryLabels.labelOf("SALICYLIC")).isEqualTo("살리실산");
        assertThat(IngredientCategoryLabels.labelOf("FRAGRANCE")).isEqualTo("향료");
        assertThat(IngredientCategoryLabels.labelOf("ALCOHOL")).isEqualTo("알코올");
        assertThat(IngredientCategoryLabels.labelOf("SPF_FILTER")).isEqualTo("자외선 차단 성분");
        assertThat(IngredientCategoryLabels.labelOf("OTHER")).isEqualTo("기타");
        // AHA·BHA는 통용 약어 — 변환하지 않는 것이 사양이다
        assertThat(IngredientCategoryLabels.labelOf("AHA")).isEqualTo("AHA");
        assertThat(IngredientCategoryLabels.labelOf("BHA")).isEqualTo("BHA");
    }

    @Test
    @DisplayName("미지 코드는 코드를 그대로 반환한다 — 조용한 누락 금지")
    void 미지_코드_통과() {
        assertThat(IngredientCategoryLabels.labelOf("SQUALANE")).isEqualTo("SQUALANE");
    }
}

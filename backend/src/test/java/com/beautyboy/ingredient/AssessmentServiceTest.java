package com.beautyboy.ingredient;

import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.common.BusinessException;
import com.beautyboy.ingredient.dto.GoodsAssessmentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    @Mock GoodsQueryService goodsQueryService;
    @Mock IngredientRegFlagRepository regFlagRepository;
    @InjectMocks AssessmentServiceImpl service;

    /** reg_flag 조인 행: [ingredientId, name, inciName, flagType, sourceRef, sortOrder]. */
    private static Object[] row(long id, String name, String inci, String flag, String ref) {
        return new Object[]{id, name, inci, flag, ref, 0};
    }

    private void stub(long goodsNo, String category, List<Object[]> rows) {
        given(goodsQueryService.exists(goodsNo)).willReturn(true);
        lenient().when(goodsQueryService.categoryCode(goodsNo)).thenReturn(category);
        given(regFlagRepository.findFlagRowsByGoodsId(goodsNo)).willReturn(rows);
    }

    @Test
    void 무플래그_leaveon은_걱정없음이고_flagged는_비어있다() {
        stub(1L, "C001002", List.<Object[]>of(row(25L, "글리세린", "glycerin", null, null)));
        GoodsAssessmentResponse r = service.assess(1L);
        assertThat(r.verdictCode()).isEqualTo("NO_CONCERN");
        assertThat(r.verdictText()).isEqualTo("걱정 성분이 거의 없어요");
        assertThat(r.flagged()).isEmpty();
    }

    @Test
    void 착향제1_각질산1_leaveon은_대체로무난_N2() {
        stub(2L, "C001002", List.of(
                row(19L, "리모넨", "limonene", "ALLERGEN", "25종"),
                row(5L, "살리실산", "salicylic acid", "EXFOLIANT_ACID", "BHA")));
        GoodsAssessmentResponse r = service.assess(2L);
        assertThat(r.verdictCode()).isEqualTo("MOSTLY_FINE");
        assertThat(r.checkCount()).isEqualTo(2);
        assertThat(r.rinseOff()).isFalse();
    }

    @Test
    void 씻어내는제품은_N에서_1을_빼고_checkCount는_보정전값() {
        stub(9L, "C002001", List.of(
                row(5L, "살리실산", "salicylic acid", "EXFOLIANT_ACID", "BHA"),
                row(19L, "리모넨", "limonene", "ALLERGEN", "25종")));
        GoodsAssessmentResponse r = service.assess(9L);
        assertThat(r.rinseOff()).isTrue();
        assertThat(r.checkCount()).isEqualTo(2);       // 표시는 보정 전
        assertThat(r.verdictCode()).isEqualTo("MOSTLY_FINE"); // adj=1
    }

    @Test
    void 확인성분5개_leaveon은_주의필요() {
        stub(3L, "C001001", List.of(
                row(19L, "리모넨", "limonene", "ALLERGEN", "a"),
                row(11L, "리날룰", "linalool", "ALLERGEN", "b"),
                row(3L, "글리콜릭", "glycolic acid", "EXFOLIANT_ACID", "AHA"),
                row(4L, "락틱", "lactic acid", "EXFOLIANT_ACID", "AHA"),
                row(5L, "살리실산", "salicylic acid", "EXFOLIANT_ACID", "BHA")));
        assertThat(service.assess(3L).verdictCode()).isEqualTo("CAUTION");
    }

    @Test
    void 한도만_있으면_판정을_올리지_않고_flagged엔_INFO로_포함한다() {
        stub(7L, "C001003", List.<Object[]>of(
                row(28L, "토코페롤", "tocopherol", "LIMIT", "* 배합한도 : ...")));
        GoodsAssessmentResponse r = service.assess(7L);
        assertThat(r.verdictCode()).isEqualTo("NO_CONCERN");
        assertThat(r.flagged()).hasSize(1);
        assertThat(r.flagged().get(0).axis()).isEqualTo("INFO");
    }

    @Test
    void 살리실산은_한_성분에_LIMIT와_EXFOLIANT_ACID가_모여_axis는_CHECK() {
        stub(8L, "C001002", List.of(
                row(5L, "살리실산", "salicylic acid", "EXFOLIANT_ACID", "BHA"),
                row(5L, "살리실산", "salicylic acid", "LIMIT", "* 배합한도 : 0.5%")));
        GoodsAssessmentResponse r = service.assess(8L);
        assertThat(r.checkCount()).isEqualTo(1);        // 한 성분이므로 각질산 1회만
        assertThat(r.flagged()).hasSize(1);
        assertThat(r.flagged().get(0).flags()).containsExactlyInAnyOrder("EXFOLIANT_ACID", "LIMIT");
        assertThat(r.flagged().get(0).axis()).isEqualTo("CHECK");
    }

    @Test
    void 금지성분이_있으면_검토필요로_반환한다() {
        stub(5L, "C001002", List.of(
                row(99L, "금지가상", "banned-x", "BANNED", null),
                row(19L, "리모넨", "limonene", "ALLERGEN", "a")));
        GoodsAssessmentResponse r = service.assess(5L);
        assertThat(r.verdictCode()).isEqualTo("REVIEW");
        assertThat(r.flagged().stream().anyMatch(f -> "REVIEW".equals(f.axis()))).isTrue();
    }

    @Test
    void 없는상품은_GOODS_NOT_FOUND() {
        given(goodsQueryService.exists(404L)).willReturn(false);
        assertThatThrownBy(() -> service.assess(404L)).isInstanceOf(BusinessException.class);
    }
}

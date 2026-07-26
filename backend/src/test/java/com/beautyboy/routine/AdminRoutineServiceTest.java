package com.beautyboy.routine;

import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminRoutineServiceTest {

    @Mock
    RoutineTemplateRepository routineTemplateRepository;
    @Mock
    GoodsQueryService goodsQueryService;
    @InjectMocks
    AdminRoutineService adminRoutineService;

    private static GoodsListItem 카드(long goodsNo) {
        return new GoodsListItem(goodsNo, "브랜드" + goodsNo, "상품" + goodsNo,
                "https://img.example/" + goodsNo + ".jpg", 10000, 9000, 10,
                List.of(), 0.0, 0, false, false, List.of());
    }

    /** 2단계 템플릿. 2단계(stepOrder=2)에 goodsNo 1,2가 이미 걸려 있다. */
    private static RoutineTemplate 두단계_템플릿() {
        List<RoutineStepGoods> 단계1_상품 = List.of(new RoutineStepGoods(1L, 100L, 1));
        RoutineStep 단계1 = new RoutineStep(1L, 1, "단계1", "팁1", 단계1_상품);

        List<RoutineStepGoods> 단계2_상품 = new ArrayList<>(List.of(
                new RoutineStepGoods(2L, 1L, 1),
                new RoutineStepGoods(3L, 2L, 2)));
        RoutineStep 단계2 = new RoutineStep(2L, 2, "단계2", "팁2", 단계2_상품);

        return new RoutineTemplate(7L, "건성 루틴", "DRY", "BASIC", "설명", List.of(단계1, 단계2));
    }

    @Test
    void 단계_추천상품을_통째로_교체하고_순서를_1부터_다시_매긴다() {
        RoutineTemplate template = 두단계_템플릿();
        given(routineTemplateRepository.findGraphById(7L)).willReturn(Optional.of(template));
        given(goodsQueryService.findListItems(any(), any()))
                .willReturn(List.of(카드(3L), 카드(1L)));

        adminRoutineService.replaceStepGoods(7L, 2, List.of(3L, 1L));

        RoutineStep 단계2 = template.getSteps().stream().filter(s -> s.getStepOrder() == 2).findFirst().orElseThrow();
        assertThat(단계2.getStepGoods())
                .extracting(RoutineStepGoods::getGoodsNo)
                .containsExactly(3L, 1L);
        assertThat(단계2.getStepGoods())
                .extracting(RoutineStepGoods::getSortOrder)
                .containsExactly(1, 2);

        // 다른 단계는 건드리지 않는다
        RoutineStep 단계1 = template.getSteps().stream().filter(s -> s.getStepOrder() == 1).findFirst().orElseThrow();
        assertThat(단계1.getStepGoods()).extracting(RoutineStepGoods::getGoodsNo).containsExactly(100L);
    }

    @Test
    void 숨겨진_상품을_단계에_넣으면_ROUTINE_STEP_GOODS_INVALID다() {
        RoutineTemplate template = 두단계_템플릿();
        given(routineTemplateRepository.findGraphById(7L)).willReturn(Optional.of(template));
        // goodsNo 999는 숨김/존재하지 않아 findListItems가 누락시킨다
        given(goodsQueryService.findListItems(any(), any())).willReturn(List.of(카드(1L)));

        assertThatThrownBy(() -> adminRoutineService.replaceStepGoods(7L, 2, List.of(1L, 999L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTINE_STEP_GOODS_INVALID);
    }

    @Test
    void 존재하지_않는_템플릿이면_ROUTINE_TEMPLATE_NOT_FOUND다() {
        given(routineTemplateRepository.findGraphById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminRoutineService.replaceStepGoods(999L, 1, List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTINE_TEMPLATE_NOT_FOUND);
    }

    @Test
    void 존재하지_않는_단계번호면_ROUTINE_STEP_NOT_FOUND다() {
        RoutineTemplate template = 두단계_템플릿();
        given(routineTemplateRepository.findGraphById(7L)).willReturn(Optional.of(template));

        assertThatThrownBy(() -> adminRoutineService.replaceStepGoods(7L, 9, List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTINE_STEP_NOT_FOUND);
    }
}

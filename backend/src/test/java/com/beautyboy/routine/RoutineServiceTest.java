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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoutineServiceTest {

    @Mock
    RoutineTemplateRepository routineTemplateRepository;
    @Mock
    GoodsQueryService goodsQueryService;
    @InjectMocks
    RoutineService routineService;

    private static GoodsListItem 카드(long goodsNo) {
        return new GoodsListItem(goodsNo, "브랜드" + goodsNo, "상품" + goodsNo,
                "https://img.example/" + goodsNo + ".jpg", 10000, 9000, 10,
                List.of(), 0.0, 0, false, false);
    }

    /** 5단계, 각 단계에 goodsNo 1,2를 추천으로 건 템플릿. */
    private static RoutineTemplate 다섯단계_템플릿(String skinType, String timeSlot) {
        List<RoutineStep> steps = new ArrayList<>();
        for (int order = 1; order <= 5; order++) {
            List<RoutineStepGoods> stepGoods = List.of(
                    new RoutineStepGoods((long) order * 10 + 1, 1L, 1),
                    new RoutineStepGoods((long) order * 10 + 2, 2L, 2));
            steps.add(new RoutineStep((long) order, order, "단계" + order, "팁" + order, stepGoods));
        }
        return new RoutineTemplate(7L, skinType + " 루틴", skinType, timeSlot, "설명", steps);
    }

    @Test
    void 피부타입으로_템플릿을_찾고_단계별_추천을_카드로_채운다() {
        given(routineTemplateRepository.findGraphBySkinTypeAndTimeSlot("DRY", "BASIC"))
                .willReturn(java.util.Optional.of(다섯단계_템플릿("DRY", "BASIC")));
        given(goodsQueryService.findListItems(any(), any())).willReturn(List.of(카드(1L), 카드(2L)));

        var response = routineService.find("DRY", "BASIC", null);

        assertThat(response.steps()).hasSize(5);
        assertThat(response.steps().get(0).stepOrder()).isEqualTo(1);
        assertThat(response.steps().get(0).recommendations()).isNotEmpty();
        assertThat(response.templateId()).isEqualTo(7L);
        assertThat(response.skinType()).isEqualTo("DRY");
    }

    @Test
    void skinType이_없으면_COMBINATION_BASIC으로_폴백한다() {
        given(routineTemplateRepository.findGraphBySkinTypeAndTimeSlot("COMBINATION", "BASIC"))
                .willReturn(java.util.Optional.of(다섯단계_템플릿("COMBINATION", "BASIC")));
        given(goodsQueryService.findListItems(any(), any())).willReturn(List.of(카드(1L), 카드(2L)));

        routineService.find(null, null, null);

        verify(routineTemplateRepository).findGraphBySkinTypeAndTimeSlot("COMBINATION", "BASIC");
    }

    @Test
    void skinType은_대문자로_정규화된다() {
        given(routineTemplateRepository.findGraphBySkinTypeAndTimeSlot("OILY", "BASIC"))
                .willReturn(java.util.Optional.of(다섯단계_템플릿("OILY", "BASIC")));
        given(goodsQueryService.findListItems(any(), any())).willReturn(List.of(카드(1L), 카드(2L)));

        routineService.find("oily", "BASIC", null);

        verify(routineTemplateRepository).findGraphBySkinTypeAndTimeSlot("OILY", "BASIC");
    }

    @Test
    void 카드가_없는_추천은_단계에서_제외된다() {
        given(routineTemplateRepository.findGraphBySkinTypeAndTimeSlot("DRY", "BASIC"))
                .willReturn(java.util.Optional.of(다섯단계_템플릿("DRY", "BASIC")));
        // goodsNo 1만 카드로 존재(2는 HIDDEN 등으로 findListItems가 누락)
        given(goodsQueryService.findListItems(any(), any())).willReturn(List.of(카드(1L)));

        var response = routineService.find("DRY", "BASIC", null);

        assertThat(response.steps().get(0).recommendations()).hasSize(1);
        assertThat(response.steps().get(0).recommendations().get(0).goodsNo()).isEqualTo(1L);
    }

    @Test
    void viewerId가_findListItems에_그대로_전달된다() {
        given(routineTemplateRepository.findGraphBySkinTypeAndTimeSlot("DRY", "BASIC"))
                .willReturn(java.util.Optional.of(다섯단계_템플릿("DRY", "BASIC")));
        given(goodsQueryService.findListItems(any(), eq(42L))).willReturn(List.of(카드(1L), 카드(2L)));

        routineService.find("DRY", "BASIC", 42L);

        verify(goodsQueryService).findListItems(any(), eq(42L));
    }

    @Test
    void 템플릿이_없으면_ROUTINE_TEMPLATE_NOT_FOUND를_던진다() {
        given(routineTemplateRepository.findGraphBySkinTypeAndTimeSlot("UNKNOWN", "BASIC"))
                .willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> routineService.find("UNKNOWN", "BASIC", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ROUTINE_TEMPLATE_NOT_FOUND));
    }
}

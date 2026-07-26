package com.beautyboy.routine;

import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.routine.dto.AdminRoutineStepResponse;
import com.beautyboy.routine.dto.AdminRoutineTemplateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminRoutineService {

    private final RoutineTemplateRepository routineTemplateRepository;
    private final GoodsQueryService goodsQueryService;

    public AdminRoutineService(RoutineTemplateRepository routineTemplateRepository,
                                GoodsQueryService goodsQueryService) {
        this.routineTemplateRepository = routineTemplateRepository;
        this.goodsQueryService = goodsQueryService;
    }

    /** 템플릿 + 단계 목록. 관리자 화면이라 카드가 아니라 추천 상품 goodsNo만 싣는다. */
    @Transactional(readOnly = true)
    public List<AdminRoutineTemplateResponse> list() {
        return routineTemplateRepository.findAllGraphs().stream()
                .map(this::toResponse)
                .toList();
    }

    // "추가"가 아니라 "전체 교체"인 이유:
    // 부분 추가/삭제 API를 두면 정렬 순서(sort_order)를 클라이언트가 관리하게 되고, 중간 삭제 시
    // 순서에 구멍이 난다. 화면도 어차피 "이 단계의 추천 상품 목록"을 통째로 편집한다.
    @Transactional
    public void replaceStepGoods(Long templateId, int stepOrder, List<Long> goodsNos) {
        RoutineTemplate template = routineTemplateRepository.findGraphById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTINE_TEMPLATE_NOT_FOUND));

        RoutineStep step = template.getSteps().stream()
                .filter(s -> s.getStepOrder() == stepOrder)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTINE_STEP_NOT_FOUND));

        // 존재하지 않거나 숨겨진 상품을 단계에 꽂으면 /routines 추천이 조용히 빈다.
        // catalog에 물어본다(경계 규칙 — goods 테이블을 직접 보지 않는다).
        List<GoodsListItem> valid = goodsQueryService.findListItems(goodsNos, null);
        if (valid.size() != goodsNos.size()) {
            throw new BusinessException(ErrorCode.ROUTINE_STEP_GOODS_INVALID);
        }

        List<RoutineStepGoods> replaced = new ArrayList<>();
        for (int i = 0; i < goodsNos.size(); i++) {
            replaced.add(new RoutineStepGoods(null, goodsNos.get(i), i + 1));
        }
        step.replaceStepGoods(replaced);
    }

    private AdminRoutineTemplateResponse toResponse(RoutineTemplate template) {
        List<AdminRoutineStepResponse> steps = template.getSteps().stream()
                .map(step -> new AdminRoutineStepResponse(
                        step.getStepOrder(),
                        step.getStepName(),
                        step.getBeginnerTip(),
                        step.getStepGoods().stream().map(RoutineStepGoods::getGoodsNo).toList()))
                .toList();
        return new AdminRoutineTemplateResponse(template.getId(), template.getName(), template.getSkinType(),
                template.getTimeSlot(), template.getDescription(), steps);
    }
}

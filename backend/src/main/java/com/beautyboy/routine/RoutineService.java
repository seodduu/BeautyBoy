package com.beautyboy.routine;

import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.routine.dto.RoutineResponse;
import com.beautyboy.routine.dto.RoutineStepResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RoutineService implements RoutineQueryService {

    private static final String DEFAULT_SKIN_TYPE = "COMBINATION";
    private static final String DEFAULT_TIME = "BASIC";

    private final RoutineTemplateRepository routineTemplateRepository;
    private final GoodsQueryService goodsQueryService;

    public RoutineService(RoutineTemplateRepository routineTemplateRepository,
                          GoodsQueryService goodsQueryService) {
        this.routineTemplateRepository = routineTemplateRepository;
        this.goodsQueryService = goodsQueryService;
    }

    @Override
    @Transactional(readOnly = true)
    public RoutineResponse find(String skinType, String time) {
        String resolvedSkinType = (skinType == null || skinType.isBlank())
                ? DEFAULT_SKIN_TYPE : skinType.toUpperCase();
        String resolvedTime = (time == null || time.isBlank()) ? DEFAULT_TIME : time;

        RoutineTemplate template = routineTemplateRepository
                .findGraphBySkinTypeAndTimeSlot(resolvedSkinType, resolvedTime)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTINE_TEMPLATE_NOT_FOUND));

        Set<RoutineStep> steps = template.getSteps();

        // N+1 방지: 전 단계 goods_no를 모아 findListItems를 정확히 한 번 호출한다.
        List<Long> allGoodsNos = steps.stream()
                .flatMap(step -> step.getStepGoods().stream())
                .map(RoutineStepGoods::getGoodsNo)
                .distinct()
                .toList();
        Map<Long, GoodsListItem> cardByGoodsNo = goodsQueryService.findListItems(allGoodsNos).stream()
                .collect(Collectors.toMap(GoodsListItem::goodsNo, Function.identity()));

        List<RoutineStepResponse> stepResponses = steps.stream()
                .map(step -> new RoutineStepResponse(
                        step.getStepOrder(),
                        step.getStepName(),
                        step.getBeginnerTip(),
                        step.getStepGoods().stream()
                                .map(link -> cardByGoodsNo.get(link.getGoodsNo()))
                                .filter(Objects::nonNull)
                                .toList()))
                .toList();

        return new RoutineResponse(template.getId(), template.getName(), template.getSkinType(),
                template.getTimeSlot(), template.getDescription(), stepResponses);
    }
}

package com.beautyboy.routine;

import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.compat.CompatQueryService;
import com.beautyboy.routine.dto.NextStepBlock;
import com.beautyboy.routine.dto.NextStepResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 다음 단계 추천 — 규칙 매칭 → 폴백 사다리 → 궁합 게이트(설계 §5).
 * routine은 자기 테이블(routine_flow_rule)만 직접 접근하고, 상품·태그·궁합은 인터페이스를 경유한다.
 */
@Service
public class NextStepService {

    /** 블록당 카드 수. 기존 RecommendedSection 스켈레톤 4와 맞춘다(설계 §4). */
    static final int BLOCK_ITEM_LIMIT = 4;
    private static final Set<String> FORWARD_KINDS = Set.of("NEXT_STEP", "BUFFER");
    private static final String PAIRED_REMOVAL = "PAIRED_REMOVAL";

    private final RoutineFlowRuleRepository ruleRepository;
    private final GoodsQueryService goodsQueryService;
    private final CompatQueryService compatQueryService;

    public NextStepService(RoutineFlowRuleRepository ruleRepository,
                           GoodsQueryService goodsQueryService,
                           CompatQueryService compatQueryService) {
        this.ruleRepository = ruleRepository;
        this.goodsQueryService = goodsQueryService;
        this.compatQueryService = compatQueryService;
    }

    @Transactional(readOnly = true)
    public NextStepResponse find(Long goodsNo, Long viewerId) {
        if (!goodsQueryService.exists(goodsNo)) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        String leafCategory = goodsQueryService.categoryCode(goodsNo);
        Set<String> slugs = goodsQueryService.tagSlugs(goodsNo);

        // 규칙 전량 로드 후 자바에서 매칭 — priority asc, id asc가 이미 보장된 순서라
        // "처음 만나는 것"이 곧 최우선 규칙이다.
        RoutineFlowRule forward = null;
        RoutineFlowRule removal = null;
        for (RoutineFlowRule rule : ruleRepository.findAllByOrderByPriorityAscIdAsc()) {
            if (!leafCategory.startsWith(rule.getFromCategoryCode())) {
                continue;
            }
            if (rule.getFromTagSlug() != null && !slugs.contains(rule.getFromTagSlug())) {
                continue;
            }
            if (forward == null && FORWARD_KINDS.contains(rule.getEdgeKind())) {
                forward = rule;      // 순방향(NEXT_STEP·BUFFER) 통틀어 1개 — 설계 §4 블록 선정 규칙
            } else if (removal == null && PAIRED_REMOVAL.equals(rule.getEdgeKind())) {
                removal = rule;
            }
            if (forward != null && removal != null) {
                break;
            }
        }

        List<NextStepBlock> blocks = new ArrayList<>();
        for (RoutineFlowRule rule : Arrays.asList(forward, removal)) {   // 순방향 먼저
            if (rule != null) {
                buildBlock(rule, goodsNo, viewerId).ifPresent(blocks::add);
            }
        }
        return new NextStepResponse(blocks);
    }

    private Optional<NextStepBlock> buildBlock(RoutineFlowRule rule, Long baseGoodsNo, Long viewerId) {
        // 1) 목표 태그 일치 후보(인기순)
        List<Long> candidates = new ArrayList<>(goodsQueryService.findCandidateGoodsNos(
                rule.getToCategoryCode(), rule.getToTagSlug(), baseGoodsNo, BLOCK_ITEM_LIMIT));
        // 2) 폴백: 부족하면 태그 조건을 떼고 같은 카테고리 인기순으로 채운다(설계 §5-3).
        //    limit을 넉넉히 뽑는 이유: 앞에서 이미 뽑힌 후보와 겹치는 만큼 걸러야 하기 때문.
        if (candidates.size() < BLOCK_ITEM_LIMIT && rule.getToTagSlug() != null) {
            for (Long no : goodsQueryService.findCandidateGoodsNos(
                    rule.getToCategoryCode(), null, baseGoodsNo, BLOCK_ITEM_LIMIT + candidates.size())) {
                if (candidates.size() >= BLOCK_ITEM_LIMIT) {
                    break;
                }
                if (!candidates.contains(no)) {
                    candidates.add(no);
                }
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        // 3) 궁합 게이트: CONFLICT 후보 제거. 제거 후 재폴백은 하지 않는다(설계 §5-4).
        Map<Long, String> verdicts = compatQueryService.worstVerdicts(baseGoodsNo, candidates);
        List<Long> safe = candidates.stream()
                .filter(no -> !"CONFLICT".equals(verdicts.get(no)))
                .toList();
        if (safe.isEmpty()) {
            return Optional.empty();
        }
        // findListItems는 입력 순서를 보존하지 않는다 — 후보 순서(태그 일치 → 인기순)로 재정렬.
        Map<Long, GoodsListItem> byNo = goodsQueryService.findListItems(safe, viewerId).stream()
                .collect(Collectors.toMap(GoodsListItem::goodsNo, item -> item));
        List<GoodsListItem> items = safe.stream().map(byNo::get).filter(Objects::nonNull).toList();
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new NextStepBlock(rule.getEdgeKind(), rule.getReason(), items));
    }
}

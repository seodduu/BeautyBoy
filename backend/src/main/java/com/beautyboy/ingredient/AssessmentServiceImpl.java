package com.beautyboy.ingredient;

import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.ingredient.dto.FlaggedIngredient;
import com.beautyboy.ingredient.dto.GoodsAssessmentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 성분 종합판정 규칙(설계 §0.3~§0.4). 판단이 들어가는 유일한 지점:
 *  ① 어느 플래그를 N에 넣는가 — ALLERGEN·EXFOLIANT_ACID만. LIMIT는 카탈로그 77%가 걸려
 *     넣으면 신호가 죽고(설계 §0.3), 함량을 몰라 위험도로 쓸 수 없어 제외한다(§13).
 *  ② 경계값 1/2/4 — cosmetics.csv 실측 분포가 64/18/10/7%로 갈리도록 잡았다.
 *  ③ rinse-off -1 — 씻어내는 제품은 접촉시간이 짧다. 정성적이라 한 단계만 완화.
 */
@Service
@Transactional(readOnly = true)
public class AssessmentServiceImpl implements AssessmentService {

    /** rinse-off(씻어내는 제품) 카테고리 접두사: 클렌징 전체 · 바디워시 · 면도날/쉐이빙. */
    static final Set<String> RINSE_PREFIX = Set.of("C002", "C00302", "C00501", "C00502");

    private final GoodsQueryService goodsQueryService;
    private final IngredientRegFlagRepository regFlagRepository;

    public AssessmentServiceImpl(GoodsQueryService goodsQueryService,
                                 IngredientRegFlagRepository regFlagRepository) {
        this.goodsQueryService = goodsQueryService;
        this.regFlagRepository = regFlagRepository;
    }

    @Override
    public GoodsAssessmentResponse assess(Long goodsNo) {
        if (!goodsQueryService.exists(goodsNo)) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        boolean rinseOff = isRinseOff(goodsQueryService.categoryCode(goodsNo));

        // 같은 inci_name의 여러 플래그 행을 성분 단위로 접는다(정렬 유지).
        // 행: [id, name, inci, summary, flag_type, source_ref, sort_order]
        Map<Long, Agg> byIngredient = new LinkedHashMap<>();
        for (Object[] r : regFlagRepository.findFlagRowsByGoodsId(goodsNo)) {
            Long ingredientId = ((Number) r[0]).longValue();
            Agg agg = byIngredient.computeIfAbsent(ingredientId,
                    k -> new Agg(ingredientId, (String) r[1], (String) r[2], (String) r[3]));
            if (r[4] != null) {
                agg.add((String) r[4], (String) r[5]);
            }
        }

        boolean banned = byIngredient.values().stream().anyMatch(a -> a.flags.contains("BANNED"));

        int checkCount = 0;
        List<FlaggedIngredient> flagged = new ArrayList<>();
        for (Agg agg : byIngredient.values()) {
            if (agg.flags.isEmpty()) {
                continue; // 무플래그 성분은 응답에서 제외(전체 성분 목록 미제공)
            }
            if (agg.flags.contains("ALLERGEN")) {
                checkCount++;
            }
            if (agg.flags.contains("EXFOLIANT_ACID")) {
                checkCount++;
            }
            flagged.add(new FlaggedIngredient(
                    agg.ingredientId, agg.name, agg.inciName, agg.summary,
                    List.copyOf(agg.flags), agg.axis(), agg.acidClass, agg.limitText));
        }

        String verdictCode;
        String verdictText;
        if (banned) {
            verdictCode = "REVIEW";
            verdictText = "성분 정보를 확인하고 있어요";
        } else {
            int adjusted = rinseOff ? Math.max(0, checkCount - 1) : checkCount;
            if (adjusted == 0) {
                verdictCode = "NO_CONCERN";
                verdictText = "걱정 성분이 거의 없어요";
            } else if (adjusted <= 2) {
                verdictCode = "MOSTLY_FINE";
                verdictText = "대체로 무난해요";
            } else if (adjusted <= 4) {
                verdictCode = "CHECK_SENSITIVE";
                verdictText = "민감한 피부는 확인이 필요해요";
            } else {
                verdictCode = "CAUTION";
                verdictText = "주의가 필요한 성분이 있어요";
            }
        }

        return new GoodsAssessmentResponse(goodsNo, verdictCode, verdictText, checkCount, rinseOff, flagged);
    }

    static boolean isRinseOff(String categoryCode) {
        return categoryCode != null && RINSE_PREFIX.stream().anyMatch(categoryCode::startsWith);
    }

    /** 성분 하나의 플래그 집계. inci_name이 같은 여러 reg_flag 행을 여기로 접는다. */
    private static final class Agg {
        final Long ingredientId;
        final String name;
        final String inciName;
        final String summary;
        final Set<String> flags = new LinkedHashSet<>();
        String acidClass;   // EXFOLIANT_ACID의 source_ref = "AHA"|"BHA"(분류)
        String limitText;   // LIMIT의 source_ref = 배합한도 원문(근거)

        Agg(Long ingredientId, String name, String inciName, String summary) {
            this.ingredientId = ingredientId;
            this.name = name;
            this.inciName = inciName;
            this.summary = summary;
        }

        void add(String flagType, String sourceRef) {
            flags.add(flagType);
            if ("EXFOLIANT_ACID".equals(flagType) && acidClass == null) {
                acidClass = sourceRef;
            } else if ("LIMIT".equals(flagType) && limitText == null) {
                limitText = sourceRef;
            }
        }

        String axis() {
            if (flags.contains("ALLERGEN") || flags.contains("EXFOLIANT_ACID")) return "CHECK";
            if (flags.contains("BANNED")) return "REVIEW";
            return "INFO";
        }
    }
}

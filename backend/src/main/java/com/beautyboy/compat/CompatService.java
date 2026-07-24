package com.beautyboy.compat;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.compat.dto.CompatCheckRequest;
import com.beautyboy.compat.dto.CompatCheckResponse;
import com.beautyboy.compat.dto.CompatFinding;
import com.beautyboy.ingredient.GoodsIngredientQueryService;
import com.beautyboy.ingredient.IngredientRule;
import com.beautyboy.ingredient.IngredientRuleRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 성분 분류쌍 궁합 진단 엔진(Wave 3 T1 Task 1-5).
 *
 * ingredient 도메인은 두 인터페이스로만 소비한다(패키지 = 서비스 경계):
 *  - {@link GoodsIngredientQueryService#findCategoriesByGoodsIds} — 상품→분류집합
 *  - {@link IngredientRuleRepository#findNormalized} — 분류쌍 규칙(사전순 정규화 조회)
 *
 * 결정적 출력: 분류를 TreeSet(사전순)로 모아 i<j로 순회하므로 findNormalized 호출·finding 생성이
 * 항상 같은 순서이고, 최종 findings는 심각도 내림차순→categoryA→categoryB로 재정렬해 고정한다.
 */
@Service
public class CompatService {

    // 심각도 순위: CONFLICT(3) > CAUTION(2) > SYNERGY(1). overall/정렬 기준.
    private static int severity(String verdict) {
        return switch (verdict) {
            case "CONFLICT" -> 3;
            case "CAUTION" -> 2;
            case "SYNERGY" -> 1;
            default -> 0;
        };
    }

    private final GoodsIngredientQueryService goodsIngredientQueryService;
    private final IngredientRuleRepository ruleRepository;

    public CompatService(GoodsIngredientQueryService goodsIngredientQueryService,
                         IngredientRuleRepository ruleRepository) {
        this.goodsIngredientQueryService = goodsIngredientQueryService;
        this.ruleRepository = ruleRepository;
    }

    public CompatCheckResponse check(CompatCheckRequest request) {
        List<Long> goodsNos = request.goodsNos();
        if (goodsNos == null || goodsNos.isEmpty()) {
            throw new BusinessException(ErrorCode.COMPAT_EMPTY_SELECTION);
        }

        Map<Long, Set<String>> cats = goodsIngredientQueryService.findCategoriesByGoodsIds(goodsNos);

        // 분류→그 분류를 가진 상품집합 역인덱스. 상품집합은 TreeSet으로 오름차순 유지.
        Map<String, TreeSet<Long>> byCategory = new java.util.HashMap<>();
        for (Map.Entry<Long, Set<String>> entry : cats.entrySet()) {
            Long goodsNo = entry.getKey();
            for (String category : entry.getValue()) {
                byCategory.computeIfAbsent(category, k -> new TreeSet<>()).add(goodsNo);
            }
        }

        // 등장한 서로 다른 분류를 사전순으로. i<j 무순서 쌍만 검사한다.
        List<String> categories = new ArrayList<>(new TreeSet<>(byCategory.keySet()));

        List<CompatFinding> findings = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            for (int j = i + 1; j < categories.size(); j++) {
                String ca = categories.get(i);
                String cb = categories.get(j);
                Optional<IngredientRule> rule = ruleRepository.findNormalized(ca, cb);
                if (rule.isEmpty()) {
                    continue;
                }
                // ca < cb 이므로 finding의 categoryA/categoryB도 사전순 고정.
                // 기여 상품 = byCategory[ca] ∪ byCategory[cb], 오름차순.
                TreeSet<Long> contributors = new TreeSet<>(byCategory.get(ca));
                contributors.addAll(byCategory.get(cb));
                findings.add(new CompatFinding(
                        rule.get().getVerdict(), ca, cb, rule.get().getReason(),
                        List.copyOf(contributors)));
            }
        }

        // 심각도 내림차순 → categoryA 사전순 → categoryB 사전순.
        findings.sort(Comparator
                .comparingInt((CompatFinding f) -> severity(f.verdict())).reversed()
                .thenComparing(CompatFinding::categoryA)
                .thenComparing(CompatFinding::categoryB));

        String overall = findings.isEmpty() ? "OK" : findings.get(0).verdict();

        return new CompatCheckResponse(overall, findings);
    }
}

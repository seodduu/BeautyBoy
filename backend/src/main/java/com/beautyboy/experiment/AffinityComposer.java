package com.beautyboy.experiment;

import com.beautyboy.experiment.dto.AffinityNextStepRequest;
import com.beautyboy.experiment.dto.AffinityNextStepResponse;
import com.beautyboy.routine.dto.ConcernRuleView;
import com.beautyboy.routine.dto.FlowRuleView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code frontend/src/features/affinity/composer.ts} + {@code profile.ts:aggregate}의 참조 이식.
 *
 * <p><b>실험 전용이다.</b> 실서비스 경로가 이 클래스를 부르지 않는다 — 부르면 {@code NextStepService}와
 * 두 개의 진실이 된다. 존재 이유는 "같은 로직을 서버에 뒀을 때의 비용"을 재는 것 하나뿐이다
 * (docs/loadtest/2026-07-30-affinity-report.md).
 *
 * <p>스프링 빈이 아니다: 정적 메서드만 두어 (a) 동등성 테스트가 컨텍스트 없이 부를 수 있고,
 * (b) 컴포넌트 스캔 대상이 아니라 기본 기동에 어떤 영향도 없다.
 *
 * <p>각 메서드 주석의 {@code composer.ts NN행} 표기는 이식의 대응점이다. TS가 바뀌면 이 표기가
 * 갈라진 지점을 가리키는 유일한 단서이므로 지우지 않는다.
 */
public final class AffinityComposer {

    // composer.ts WEIGHTS — 값의 근거는 설계 §2.1. 여기 값만 바꾸면 동등성 테스트가 깨진다.
    static final double W_CONCERN = 2.0;
    static final double W_BEHAVIOR = 1.5;
    static final double W_FLOW = 1.0;
    static final double W_TEXTURE = 0.5;
    static final double W_POPULARITY = 0.3;

    /** composer.ts ALTERNATIVE_COUNT = 3 (픽 1 + 대안 3 = 한 줄 4칸). */
    static final int ALTERNATIVE_COUNT = 3;

    /** 규칙 category_code 길이(중분류 7자). 단계 코드가 이보다 길면 앞 7자로 자른다. */
    private static final int CAT3_LENGTH = 7;

    /** composer.ts KIND_RANK — BUFFER > NEXT_STEP > PAIRED_REMOVAL. 모르는 kind는 맨 뒤(9). */
    private static final Map<String, Integer> KIND_RANK =
            Map.of("BUFFER", 0, "NEXT_STEP", 1, "PAIRED_REMOVAL", 2);
    private static final int KIND_RANK_UNKNOWN = 9;

    private AffinityComposer() {
    }

    /** profile.ts {@code aggregate()} — 키는 {@code cat3|tag}, 값은 가중치 합. */
    public static Map<String, Integer> aggregate(List<AffinityNextStepRequest.Event> events) {
        Map<String, Integer> scores = new HashMap<>();
        if (events == null) {
            return scores;
        }
        for (AffinityNextStepRequest.Event event : events) {
            for (String tag : event.tags()) {
                scores.merge(event.cat3() + "|" + tag, event.w(), Integer::sum);
            }
        }
        return scores;
    }

    /**
     * useComposer.ts의 체인 — 단계를 순서대로 확정하고, 확정된 픽이 다음 단계의 앵커가 된다.
     * 픽이 없으면 앵커도 null이지만 체인은 멈추지 않는다(useComposer.ts {@code anchor: pick ? … : null}).
     */
    public static List<AffinityNextStepResponse.StepComposition> composeChain(
            List<AffinityNextStepRequest.Step> steps,
            AffinityNextStepRequest.Signals signals,
            Map<String, Integer> affinity,
            List<AffinityNextStepRequest.Conflict> conflicts,
            List<FlowRuleView> flowRules,
            List<ConcernRuleView> concernRules) {

        List<AffinityNextStepResponse.StepComposition> out = new ArrayList<>(steps.size());
        AffinityNextStepRequest.Candidate prevPick = null;

        for (AffinityNextStepRequest.Step step : steps) {
            // 궁합 게이트 — conflicts가 null이거나 앵커가 없으면 게이트 없음(클라 verdicts=null).
            final AffinityNextStepRequest.Candidate anchor = prevPick;
            Set<Long> conflicting = (conflicts == null || anchor == null)
                    ? null
                    : conflicts.stream()
                            .filter(c -> c.base() == anchor.goodsNo())
                            .map(AffinityNextStepRequest.Conflict::goodsNo)
                            .collect(Collectors.toSet());

            AffinityNextStepResponse.StepComposition composition =
                    composeStep(step, signals, affinity, anchor, conflicting, flowRules, concernRules);
            out.add(composition);

            // 다음 앵커. 클라는 GoodsListItem에 카테고리가 없어 클렌징 단계에서만 상세를 한 번 더 보지만
            // (useComposer.ts needsCat3), 여기서는 후보가 cat3를 이미 갖고 있어 그 왕복이 없다.
            // 계산 결과는 같고 IO만 다르다 — 그 차이는 리포트 한계 절에 적혀 있다.
            prevPick = candidateOf(step, composition.pick());
        }
        return out;
    }

    /** composer.ts {@code composeStep()}(55~127행)의 이식. */
    static AffinityNextStepResponse.StepComposition composeStep(
            AffinityNextStepRequest.Step step,
            AffinityNextStepRequest.Signals signals,
            Map<String, Integer> affinity,
            AffinityNextStepRequest.Candidate prevPick,
            Set<Long> conflicting,
            List<FlowRuleView> flowRules,
            List<ConcernRuleView> concernRules) {

        // composer.ts 70~76행: 이 단계에 속하는 친화도만 태그별로 합산한다. **접두사 매칭이다** —
        // Map.get(직접 조회)을 쓰면 클렌징(단계 코드 4자 C002)이 이벤트 키(중분류 7자 C002001)와
        // 영영 일치하지 않는다. 이식에서 가장 틀리기 쉬운 곳이라 골든 케이스 26번이 이것만 본다.
        Map<String, Integer> affinityByTag = new HashMap<>();
        for (Map.Entry<String, Integer> entry : affinity.entrySet()) {
            int bar = entry.getKey().indexOf('|');
            String cat3 = entry.getKey().substring(0, bar);
            String tag = entry.getKey().substring(bar + 1);
            if (cat3.startsWith(step.categoryCode())) {
                affinityByTag.merge(tag, entry.getValue(), Integer::sum);
            }
        }

        // composer.ts 79~84행: 궁합 게이트. 후보가 전부 걸리면 pick=null로 끝난다.
        List<AffinityNextStepRequest.Candidate> gated = step.candidates().stream()
                .filter(c -> conflicting == null || !conflicting.contains(c.goodsNo()))
                .toList();
        if (gated.isEmpty()) {
            return new AffinityNextStepResponse.StepComposition(
                    step.id(), null, List.of(), null, List.of(), List.of());
        }

        // composer.ts 87~88행: 이전 픽에서 이 단계를 겨냥한 전이 규칙 1개.
        FlowRuleView flowRule = prevPick == null ? null : pickFlowRule(prevPick, step, flowRules);
        String flowTag = flowRule == null ? null : flowRule.toTagSlug();

        // composer.ts 91~95행: behavior는 후보 중 최대값으로 정규화한다. 전부 0이면 0(0으로 나누지 않는다).
        int n = gated.size();
        int[] raw = new int[n];
        for (int i = 0; i < n; i++) {
            int sum = 0;
            for (String slug : gated.get(i).tags()) {
                sum += affinityByTag.getOrDefault(slug, 0);
            }
            raw[i] = sum;
        }
        int maxAffinity = Arrays.stream(raw).max().orElse(0);

        // composer.ts 97~111행
        List<Scored> scored = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            AffinityNextStepRequest.Candidate candidate = gated.get(i);
            List<String> slugs = candidate.tags();
            List<String> concernHits = slugs.stream().filter(s -> signals.concerns().contains(s)).toList();
            List<String> behaviorHits = slugs.stream()
                    .filter(s -> affinityByTag.getOrDefault(s, 0) > 0).toList();
            // 단독 오버라이드는 히트 1개도 만점(2)으로 정규화한다 — composer.ts ComposerSignals 주석.
            int concernUnits = signals.concernOverride() && !concernHits.isEmpty()
                    ? 2 : Math.min(concernHits.size(), 2);
            double score = W_CONCERN * concernUnits
                    + W_BEHAVIOR * (maxAffinity > 0 ? (double) raw[i] / maxAffinity : 0.0)
                    + W_FLOW * (flowTag != null && slugs.contains(flowTag) ? 1 : 0)
                    + W_TEXTURE * Math.min(slugs.stream().filter(s -> signals.textures().contains(s)).count(), 1)
                    + W_POPULARITY * ((double) (n - i) / n);
            scored.add(new Scored(candidate, i, score, concernHits, behaviorHits));
        }

        // composer.ts 114행: 점수 내림차순 → 서버 인기순(i) → goodsNo.
        // 이 비교자는 전순서다(같은 i가 둘일 수 없다) — 그래서 두 언어의 정렬 구현 차이(안정성 여부)가
        // 결과를 바꿀 수 없다. 이것이 동등성이 성립하는 구조적 근거다.
        scored.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparingInt(Scored::index)
                .thenComparingLong(s -> s.candidate().goodsNo()));

        Scored top = scored.get(0);
        // composer.ts 118~119행: reason 폴백 사다리 — 전이 규칙 → 고민 규칙 → null.
        String reason = flowRule != null
                ? flowRule.reason()
                : pickConcernReason(step, signals.concerns(), concernRules);

        return new AffinityNextStepResponse.StepComposition(
                step.id(),
                top.candidate().goodsNo(),
                scored.subList(1, Math.min(1 + ALTERNATIVE_COUNT, scored.size())).stream()
                        .map(s -> s.candidate().goodsNo()).toList(),
                reason,
                top.concernHits(),
                top.behaviorHits());
    }

    /**
     * composer.ts {@code pickFlowRule()}(133~150행) — kind 서열 → priority 오름차순의 첫 행.
     *
     * <p>TS는 {@code sort} 후 {@code [0]}을 취하고 JS sort는 안정 정렬이라 동순위면 입력 순서가 남는다.
     * {@code Stream.min}도 "첫 최소"를 반환하므로 같은 행을 고른다 — 골든 케이스가 이를 확인한다.
     */
    static FlowRuleView pickFlowRule(AffinityNextStepRequest.Candidate prevPick,
                                     AffinityNextStepRequest.Step step,
                                     List<FlowRuleView> rules) {
        String stepPrefix = cat3Prefix(step.categoryCode());
        return rules.stream()
                .filter(r -> prevPick.cat3().startsWith(r.fromCategoryCode())
                        && (r.fromTagSlug() == null || prevPick.tags().contains(r.fromTagSlug()))
                        && r.toCategoryCode().startsWith(stepPrefix))
                .min(Comparator.<FlowRuleView>comparingInt(
                                r -> KIND_RANK.getOrDefault(r.edgeKind(), KIND_RANK_UNKNOWN))
                        .thenComparingInt(FlowRuleView::priority))
                .orElse(null);
    }

    /**
     * composer.ts {@code pickConcernReason()}(152~166행) — 고민 <b>선택 순서</b>대로 훑고,
     * 각 고민 안에서 priority 최소를 고른다. 순서가 곧 티어1의 우선순위다.
     */
    static String pickConcernReason(AffinityNextStepRequest.Step step, List<String> concerns,
                                    List<ConcernRuleView> rules) {
        String stepPrefix = cat3Prefix(step.categoryCode());
        for (String concern : concerns) {
            String reason = rules.stream()
                    .filter(r -> r.concernTagSlug().equals(concern)
                            && r.toCategoryCode().startsWith(stepPrefix))
                    .min(Comparator.comparingInt(ConcernRuleView::priority))
                    .map(ConcernRuleView::reason)
                    .orElse(null);
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }

    /** TS {@code slice(0, 7)}과 같게 자른다 — 7자보다 짧은 코드(클렌징 C002)는 그대로 남는다. */
    private static String cat3Prefix(String categoryCode) {
        return categoryCode.substring(0, Math.min(CAT3_LENGTH, categoryCode.length()));
    }

    /** 픽 goodsNo → 그 단계 후보의 원본 행. 다음 단계 앵커로 쓴다. */
    private static AffinityNextStepRequest.Candidate candidateOf(AffinityNextStepRequest.Step step,
                                                                Long pick) {
        if (pick == null) {
            return null;
        }
        return step.candidates().stream()
                .filter(c -> c.goodsNo() == pick)
                .findFirst()
                .orElse(null);
    }

    /** 점수 계산 중간값. index는 서버 인기순 위치(동점 tie-break 축). */
    private record Scored(AffinityNextStepRequest.Candidate candidate, int index, double score,
                          List<String> concernHits, List<String> behaviorHits) {
    }
}

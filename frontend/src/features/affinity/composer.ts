import type { GoodsListItem } from '../../types/goods';
import type { ConcernRuleView, FlowRuleView } from '../../types/routine';
import type { RoutineStep } from '../routine/steps';

/** 가중치 — 설계 §2.1 표가 근거의 유일한 출처. 여기 값만 바꾸고 근거를 안 고치면 안 된다. */
export const WEIGHTS = {
  concern: 2.0,
  behavior: 1.5,
  flow: 1.0,
  texture: 0.5,
  popularity: 0.3,
} as const;

/** 단계당 후보 풀 크기(서버 인기순, 태그 필터 없음). 설계 §9 — 12위 밖은 개인화로도 못 올라온다. */
export const POOL_SIZE = 12;

/** 대안 수 (픽 1 + 대안 3 = 기존 한 줄 4칸 유지). */
export const ALTERNATIVE_COUNT = 3;

export interface ComposerSignals {
  /** 고민 슬러그 — 사용감 제외, 피부타입 파생 포함 (profile.ts의 effectiveConcerns 결과). */
  concerns: string[];
  /** 선호 사용감 슬러그 (preferredTextures 결과). */
  textures: string[];
  /** aggregate() 결과 — key `${cat3}|${tag}`, value 가중치 합. */
  affinity: Map<string, number>;
}

export interface PrevPick {
  goodsNo: number;
  cat3: string; // 중분류 7자
  tags: string[]; // slug[]
}

export interface StepComposition {
  /** null = 후보 풀이 비었음 → 화면은 기준선 그리드로 폴백(픽 카드 미렌더). */
  pick: GoodsListItem | null;
  alternatives: GoodsListItem[]; // 최대 ALTERNATIVE_COUNT
  /** 발동 규칙의 reason 원문. null이면 문장 없이 근거 칩만(설계 §3.2 폴백 사다리). */
  reason: string | null;
  /** 픽 카드 근거 칩 — 점수에 실제 기여한 태그만(설계 §5). */
  matched: { concerns: string[]; behaviors: string[] };
}

export function composeStep(input: {
  step: RoutineStep;
  candidates: GoodsListItem[]; // 서버 인기순 ≤ POOL_SIZE
  signals: ComposerSignals;
  prevPick: PrevPick | null;
  flowRules: FlowRuleView[];
  concernRules: ConcernRuleView[];
  /** goodsNo → 최악 verdict. null = 게이트 호출 실패(전부 통과 — 설계 §3.3). */
  verdicts: Map<number, string> | null;
}): StepComposition {
  const { step, signals, prevPick, flowRules, concernRules, verdicts } = input;

  // 이 단계에 속하는 행동 친화도만 태그별로 합산한다. Map.get 직접 조회를 쓰면 안 된다 —
  // 이벤트 키는 중분류 7자(C002001|tag)인데 클렌징 단계의 categoryCode는 대분류 4자(C002)라
  // 키가 영영 일치하지 않는다. 접두사 매칭으로 합산해야 단계 깊이 혼재(steps.ts 주석)를 견딘다.
  const affinityByTag = new Map<string, number>();
  for (const [key, w] of signals.affinity) {
    const [cat3, tag] = key.split('|');
    if (cat3.startsWith(step.categoryCode)) {
      affinityByTag.set(tag, (affinityByTag.get(tag) ?? 0) + w);
    }
  }

  // 1) 궁합 게이트 — 이전 픽과 CONFLICT면 후보에서 제거.
  const gated = input.candidates.filter(
    (p) => verdicts === null || verdicts.get(p.goodsNo) !== 'CONFLICT',
  );
  if (gated.length === 0) {
    return { pick: null, alternatives: [], reason: null, matched: { concerns: [], behaviors: [] } };
  }

  // 2) 전이 규칙 — 이전 픽에서 이 단계를 겨냥한 규칙 1개 (설계 §3.2).
  const flowRule = prevPick ? pickFlowRule(prevPick, step, flowRules) : null;
  const flowTag = flowRule?.toTagSlug ?? null;

  // 3) 점수 — behaviorAffinity는 후보 중 최대값 정규화(전부 0이면 0).
  const rawAffinity = gated.map((p) =>
    p.tags.reduce((sum, t) => sum + (affinityByTag.get(t.slug) ?? 0), 0),
  );
  const maxAffinity = Math.max(...rawAffinity);
  const n = gated.length;

  const scored = gated.map((p, i) => {
    const slugs = p.tags.map((t) => t.slug);
    const concernHits = slugs.filter((s) => signals.concerns.includes(s));
    const behaviorHits = slugs.filter((s) => (affinityByTag.get(s) ?? 0) > 0);
    const score =
      WEIGHTS.concern * Math.min(concernHits.length, 2) +
      WEIGHTS.behavior * (maxAffinity > 0 ? rawAffinity[i] / maxAffinity : 0) +
      WEIGHTS.flow * (flowTag !== null && slugs.includes(flowTag) ? 1 : 0) +
      WEIGHTS.texture * Math.min(slugs.filter((s) => signals.textures.includes(s)).length, 1) +
      WEIGHTS.popularity * ((n - i) / n);
    return { p, i, score, concernHits, behaviorHits };
  });

  // 4) 정렬 — 점수 내림차순, 동점은 서버 인기순(i) → goodsNo. 완전 결정적(설계 §2.1).
  scored.sort((a, b) => b.score - a.score || a.i - b.i || a.p.goodsNo - b.p.goodsNo);

  const top = scored[0];
  // 5) reason 폴백 사다리: 전이 규칙 → 이 단계를 겨냥한 고민 규칙(고민 선택 순 → priority) → null.
  const reason =
    flowRule?.reason ?? pickConcernReason(step, signals.concerns, concernRules) ?? null;

  return {
    pick: top.p,
    alternatives: scored.slice(1, 1 + ALTERNATIVE_COUNT).map((s) => s.p),
    reason,
    matched: { concerns: top.concernHits, behaviors: top.behaviorHits },
  };
}

/** 전이 규칙 선택 — kind 서열(BUFFER > NEXT_STEP > PAIRED_REMOVAL) → priority 오름차순.
 *  priority는 이 안에서만 쓴다 — 단계 간 경쟁에는 쓰지 않는다(설계 §2.1). */
const KIND_RANK: Record<string, number> = { BUFFER: 0, NEXT_STEP: 1, PAIRED_REMOVAL: 2 };

export function pickFlowRule(
  prevPick: PrevPick,
  step: RoutineStep,
  rules: FlowRuleView[],
): FlowRuleView | null {
  const matched = rules
    .filter(
      (r) =>
        prevPick.cat3.startsWith(r.fromCategoryCode) &&
        (r.fromTagSlug === null || prevPick.tags.includes(r.fromTagSlug)) &&
        r.toCategoryCode.startsWith(step.categoryCode.slice(0, 7)),
    )
    .sort(
      (a, b) =>
        (KIND_RANK[a.edgeKind] ?? 9) - (KIND_RANK[b.edgeKind] ?? 9) || a.priority - b.priority,
    );
  return matched[0] ?? null;
}

export function pickConcernReason(
  step: RoutineStep,
  concerns: string[],
  rules: ConcernRuleView[],
): string | null {
  for (const c of concerns) {
    const hit = rules
      .filter(
        (r) => r.concernTagSlug === c && r.toCategoryCode.startsWith(step.categoryCode.slice(0, 7)),
      )
      .sort((a, b) => a.priority - b.priority)[0];
    if (hit) return hit.reason;
  }
  return null;
}

import { ROUTINE_STEPS } from '../routine/steps';
import type { ConcernRuleView, FlowRuleView } from '../../types/routine';
import type { TagView } from '../../types/goods';
import type { AffinityEvent } from './events';

/**
 * 티어1·2 매칭 — 규칙 + 신호 → 섹션별 목표(설계 §6.3·§6.4). 순수 함수만 둔다.
 */

/** 개인화 섹션 상한. 5단계가 전부 바뀌면 개인화가 아니라 "다른 화면"으로 읽힌다.
 *  2개면 나머지 3개가 기준선이 되어 무엇이 바뀌었는지 사용자가 알아볼 수 있다. */
const MAX_PERSONALIZED_SECTIONS = 2;

/** 목표 to_tag가 고민에 있을 때의 배수. 고정값을 더하면 이벤트가 적을 때 프로필이 전부를
 *  결정하고 많을 때는 아무 영향도 없다. 비율이면 어느 쪽에서도 "거들기"로 작동한다. */
const CONCERN_BOOST = 1.5;

export interface Target {
  stepId: string; // ROUTINE_STEPS의 id
  /** to_tag_slug. flowRules에는 NULL 행이 있다 — 그때는 이유 문장만 붙고 목록은 기본 쿼리다. */
  tag: string | null;
  reason: string;
}

/** 티어2 — 행동 신호가 있을 때. flowRules를 이벤트 점수로 매칭하고 프로필로 가산한다. */
export function matchByBehavior(
  events: AffinityEvent[],
  rules: FlowRuleView[],
  concerns: string[],
): Target[] {
  const scored = rules
    .map((r) => {
      // from 매칭: 중분류 접두사 일치 + (태그 무관이거나 상품 태그에 포함)
      const base = events
        .filter(
          (e) =>
            e.cat3.startsWith(r.fromCategoryCode) &&
            (r.fromTagSlug == null || e.tags.includes(r.fromTagSlug)),
        )
        .reduce((sum, e) => sum + e.w, 0);
      if (base === 0) return null;
      const boosted =
        r.toTagSlug != null && concerns.includes(r.toTagSlug) ? base * CONCERN_BOOST : base;
      return { rule: r, score: boosted };
    })
    .filter((x): x is { rule: FlowRuleView; score: number } => x !== null)
    // priority 오름차순이 먼저다 — BUFFER(10)가 NEXT_STEP(20)을 이긴다(next-step 설계 §4).
    // 점수가 아무리 높아도 완충이 먼저라는 것이 규칙의 의도다.
    .sort((a, b) => a.rule.priority - b.rule.priority || b.score - a.score);

  return takeTopPerStep(scored.map((s) => s.rule));
}

/** 티어1 — 프로필만. concernRules를 고민 선택 순서로 훑는다. */
export function matchByProfile(concerns: string[], rules: ConcernRuleView[]): Target[] {
  const ordered = concerns.flatMap((c) =>
    rules.filter((r) => r.concernTagSlug === c).sort((a, b) => a.priority - b.priority),
  );
  return takeTopPerStep(ordered);
}

/** 공통 마무리: to_category → STEP 매핑, STEP당 1개, 최대 2섹션.
 *  입력 배열은 이미 우선순위 순이므로 먼저 온 것이 이긴다. */
function takeTopPerStep(
  rules: { toCategoryCode: string; toTagSlug: string | null; reason: string }[],
): Target[] {
  const used = new Set<string>();
  const out: Target[] = [];
  for (const r of rules) {
    // startsWith 방향이 이쪽인 이유: ROUTINE_STEPS의 클렌징은 대분류 C002(4자)이고 나머지는
    // 중분류 7자다(steps.ts "단계 깊이가 섞이는 것은 의도적"). 규칙의 to_category_code는 항상
    // 7자이므로 규칙 쪽이 STEP 코드로 시작하는지를 봐야 둘 다 맞는다.
    const step = ROUTINE_STEPS.find((s) => r.toCategoryCode.startsWith(s.categoryCode));
    if (!step || used.has(step.id)) continue; // 매핑 안 되는 카테고리는 조용히 건너뛴다
    used.add(step.id);
    out.push({ stepId: step.id, tag: r.toTagSlug, reason: r.reason });
    if (out.length === MAX_PERSONALIZED_SECTIONS) break;
  }
  return out;
}

/**
 * 사용감 tie-break(설계 §6.5) — 선호 사용감을 많이 가진 후보를 앞으로 당기는 **안정 정렬**.
 * 일치 개수가 같으면 서버가 준 인기순을 그대로 둔다. 사용감은 "무엇을 추천할까"가 아니라
 * "같은 후보 중 무엇을 앞에 둘까"의 축이라 점수 계산에는 관여하지 않는다.
 */
export function rankByTexture<T extends { tags?: TagView[] }>(items: T[], textures: string[]): T[] {
  if (textures.length === 0) {
    return items;
  }
  const matches = (item: T) =>
    (item.tags ?? []).filter((tag) => textures.includes(tag.slug)).length;
  // Array.prototype.sort는 안정 정렬이다(ES2019) — 동점이면 입력 순서가 유지된다.
  return [...items].sort((a, b) => matches(b) - matches(a));
}

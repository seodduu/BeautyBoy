/**
 * 골든 케이스·벤치 공용 하네스.
 *
 * `simulation.test.ts`의 픽스처 생성 로직(스냅샷 → 단계별 인기순 상위 12 풀, 프로필 20종,
 * 결정적 행동 이벤트)을 그대로 재사용해 "클라이언트가 실제로 계산하는 것"을 재현한다.
 * **여기서 쓰는 계산 함수는 전부 프로덕션 모듈에서 import한다** — 벤치를 위해 로직을 복사하면
 * 재는 대상이 실제 코드가 아니게 된다.
 *
 * 파일명이 `*.bench.ts`가 아니므로 vitest가 이 파일을 테스트로 수집하지 않는다.
 */
import type { GoodsListItem } from '../src/types/goods';
import type { ConcernRuleView, FlowRuleView } from '../src/types/routine';
import { ROUTINE_STEPS, type RoutineStep } from '../src/features/routine/steps';
import type { AffinityEvent } from '../src/features/affinity/events';
import { MAX_EVENTS, WEIGHT, toCat3 } from '../src/features/affinity/events';
import { aggregate, effectiveConcerns, preferredTextures } from '../src/features/affinity/profile';
import type { ComposerSignals, PrevPick, StepComposition } from '../src/features/affinity/composer';
import { POOL_SIZE, composeStep } from '../src/features/affinity/composer';
import snapshot from '../src/features/affinity/fixtures/catalog-snapshot.json';
import rulesFixture from './fixtures/rules.json';

export interface SnapshotGoods {
  goodsNo: number;
  name: string;
  categoryCode: string;
  viewCount: number;
  tags: string[];
}

const GOODS: SnapshotGoods[] = snapshot.goods;

export const FLOW_RULES = rulesFixture.flowRules as FlowRuleView[];
export const CONCERN_RULES = rulesFixture.concernRules as ConcernRuleView[];

/** 서버가 내려주는 단계별 풀의 재현 — 카테고리 접두사 필터 → 인기(viewCount)순 → 상위 12. */
function poolRowsOf(step: RoutineStep): SnapshotGoods[] {
  return GOODS.filter((g) => g.categoryCode.startsWith(step.categoryCode))
    .sort((a, b) => b.viewCount - a.viewCount || a.goodsNo - b.goodsNo)
    .slice(0, POOL_SIZE);
}

/** 조합기가 보는 필드는 goodsNo와 tags뿐이다 — 나머지는 형태를 맞추기 위한 자리값. */
function toListItem(row: SnapshotGoods): GoodsListItem {
  return {
    goodsNo: row.goodsNo,
    brandName: '브랜드',
    name: row.name,
    thumbnailUrl: `/images/goods/${row.goodsNo}.jpg`,
    listPrice: 20000,
    salePrice: 18000,
    discountRate: 10,
    badges: [],
    rating: 4.5,
    reviewCount: 10,
    wished: false,
    todayDreamAvailable: false,
    tags: row.tags.map((slug) => ({ name: slug, kind: 'EFFECT' as const, slug })),
  };
}

export const POOLS: { step: RoutineStep; rows: SnapshotGoods[]; items: GoodsListItem[] }[] =
  ROUTINE_STEPS.map((step) => {
    const rows = poolRowsOf(step);
    return { step, rows, items: rows.map(toListItem) };
  });

const ROW_BY_GOODS_NO = new Map(GOODS.map((g) => [g.goodsNo, g]));

function prevPickOf(pick: GoodsListItem): PrevPick {
  const row = ROW_BY_GOODS_NO.get(pick.goodsNo);
  if (row === undefined) {
    throw new Error(`스냅샷에 없는 goodsNo: ${pick.goodsNo}`);
  }
  return {
    goodsNo: pick.goodsNo,
    cat3: toCat3(row.categoryCode),
    tags: pick.tags.map((t) => t.slug),
  };
}

/** 서버 요청과 같은 의미의 CONFLICT 쌍. null이면 게이트 없음. */
export interface Conflict {
  base: number;
  goodsNo: number;
}

/**
 * 클라이언트 체인 — useComposer의 5단계 순차 확정을 재현한다. **벤치가 재는 단위가 이 함수다**
 * (사용자가 메인 화면 하나에서 기다리는 계산 전량).
 */
export function composeChain(
  signals: ComposerSignals,
  conflicts: Conflict[] | null = null,
): StepComposition[] {
  const compositions: StepComposition[] = [];
  let prevPick: PrevPick | null = null;

  for (const { step, items } of POOLS) {
    const base = prevPick;
    const verdicts =
      conflicts === null || base === null
        ? null
        : new Map<number, string>(
            conflicts
              .filter((c) => c.base === base.goodsNo)
              .map((c) => [c.goodsNo, 'CONFLICT' as string]),
          );
    const composition = composeStep({
      step,
      candidates: items,
      signals,
      prevPick: base,
      flowRules: FLOW_RULES,
      concernRules: CONCERN_RULES,
      verdicts,
    });
    compositions.push(composition);
    prevPick = composition.pick === null ? null : prevPickOf(composition.pick);
  }
  return compositions;
}

// ── 프로필 20종 — simulation.test.ts와 같은 결정적 조합 ──────────────────────────────

const CONCERN_SETS: string[][] = [
  [],
  ['moisture'],
  ['sebum', 'pore'],
  ['bright', 'anti-aging', 'soothe'],
];
const SKIN_TYPES = ['DRY', 'OILY', 'COMBINATION', 'SENSITIVE'] as const;
const TEXTURES = ['fresh', 'dewy', 'matte'];
const BEHAVIOR_PATTERNS = ['none', 'views', 'carts'] as const;
type BehaviorPattern = (typeof BEHAVIOR_PATTERNS)[number];

export const PROFILE_COUNT = 20;

/** 행동 이벤트를 결정적으로 만든다. 프로필 인덱스가 어떤 상품을 보는지를 바꾼다. */
export function behaviorEvents(
  profileIndex: number,
  pattern: Exclude<BehaviorPattern, 'none'>,
  count: number,
): AffinityEvent[] {
  const w = pattern === 'views' ? WEIGHT.view : WEIGHT.cart;
  const events: AffinityEvent[] = [];
  for (let n = 0; n < count; n += 1) {
    const pool = POOLS[(profileIndex + n) % POOLS.length];
    const row = pool.rows[(profileIndex * 3 + n * 5) % pool.rows.length];
    events.push({ goodsNo: row.goodsNo, cat3: toCat3(row.categoryCode), tags: row.tags, w });
  }
  return events;
}

export interface BenchProfile {
  index: number;
  events: AffinityEvent[];
  signals: ComposerSignals;
}

export function profileAt(index: number, eventCount?: number): BenchProfile {
  const rawConcerns = [...CONCERN_SETS[index % 4], TEXTURES[index % 3]];
  const skinType = SKIN_TYPES[Math.floor(index / 4) % 4];
  const pattern = BEHAVIOR_PATTERNS[index % 3];
  const count = eventCount ?? (pattern === 'views' ? 8 : 4);
  const events = pattern === 'none' ? [] : behaviorEvents(index, pattern, count);
  return {
    index,
    events,
    signals: {
      concerns: effectiveConcerns(rawConcerns, skinType),
      textures: preferredTextures(rawConcerns),
      affinity: aggregate(events),
    },
  };
}

// ── 서버 요청 형태로의 변환 ────────────────────────────────────────────────────────

export interface ServerCandidate {
  goodsNo: number;
  cat3: string;
  tags: string[];
}

export interface ServerStep {
  id: string;
  categoryCode: string;
  candidates: ServerCandidate[];
}

/** 요청 바디의 steps — 후보는 조합기가 실제로 읽는 3필드만 실는다. */
export const SERVER_STEPS: ServerStep[] = POOLS.map(({ step, rows }) => ({
  id: step.id,
  categoryCode: step.categoryCode,
  candidates: rows.map((row) => ({
    goodsNo: row.goodsNo,
    cat3: toCat3(row.categoryCode),
    tags: row.tags,
  })),
}));

export interface ServerRequest {
  steps: ServerStep[];
  signals: { concerns: string[]; textures: string[]; concernOverride: boolean };
  events: AffinityEvent[];
  conflicts: Conflict[] | null;
}

export function toServerRequest(
  signals: ComposerSignals,
  events: AffinityEvent[],
  conflicts: Conflict[] | null = null,
): ServerRequest {
  return {
    steps: SERVER_STEPS,
    signals: {
      concerns: signals.concerns,
      textures: signals.textures,
      concernOverride: signals.concernOverride ?? false,
    },
    events,
    conflicts,
  };
}

/** 응답과 비교할 형태로 클라 결과를 접는다(상품 전량 대신 goodsNo만). */
export function toExpected(compositions: StepComposition[]) {
  return compositions.map((c, i) => ({
    stepId: POOLS[i].step.id,
    pick: c.pick === null ? null : c.pick.goodsNo,
    alternatives: c.alternatives.map((a) => a.goodsNo),
    reason: c.reason,
    matchedConcerns: c.matched.concerns,
    matchedBehaviors: c.matched.behaviors,
  }));
}

export { MAX_EVENTS };

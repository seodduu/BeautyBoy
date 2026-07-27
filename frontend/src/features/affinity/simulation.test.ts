import { describe, expect, it } from 'vitest';
import type { SkinType } from '../../api/auth';
import type { GoodsListItem } from '../../types/goods';
import type { ConcernRuleView, FlowRuleView } from '../../types/routine';
import { ROUTINE_STEPS } from '../routine/steps';
import type { RoutineStep } from '../routine/steps';
import type { AffinityEvent } from './events';
import { WEIGHT, toCat3 } from './events';
import { aggregate, effectiveConcerns, preferredTextures } from './profile';
import type { ComposerSignals, PrevPick, StepComposition } from './composer';
import { ALTERNATIVE_COUNT, POOL_SIZE, composeStep } from './composer';
import snapshot from './fixtures/catalog-snapshot.json';

/**
 * 설계 §6 성과 측정 — 실데이터 스냅샷(V83 적용 후 비HIDDEN 184상품) 위에서 조합기의
 * **구조적 성질**만 검증한다. "추천 정확도"는 A/B 인프라가 없어 주장하지 않는다(설계 §9).
 *
 * 임계는 전부 **하한 보증**이다. 미달하면 가중치를 만지지 말고 실측값과 함께 보고한다.
 */

interface SnapshotGoods {
  goodsNo: number;
  name: string;
  categoryCode: string;
  viewCount: number;
  tags: string[];
}

const GOODS: SnapshotGoods[] = snapshot.goods;

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

const POOLS: { step: RoutineStep; rows: SnapshotGoods[]; items: GoodsListItem[] }[] =
  ROUTINE_STEPS.map((step) => {
    const rows = poolRowsOf(step);
    return { step, rows, items: rows.map(toListItem) };
  });

const ROW_BY_GOODS_NO = new Map(GOODS.map((g) => [g.goodsNo, g]));

// ── 규칙 픽스처 — V75(전이 12행)·V82(고민 19행) 시드를 그대로 옮긴다. MSW 불필요. ──────────

function flow(
  fromCategoryCode: string,
  fromTagSlug: string | null,
  toCategoryCode: string,
  toTagSlug: string | null,
  edgeKind: FlowRuleView['edgeKind'],
  reason: string,
  priority: number,
): FlowRuleView {
  return { fromCategoryCode, fromTagSlug, toCategoryCode, toTagSlug, edgeKind, reason, priority };
}

const FLOW_RULES: FlowRuleView[] = [
  flow('C002001', 'exfoliate', 'C001001', 'soothe', 'BUFFER', '피지·각질까지 씻어낸 다음엔 진정 토너로 완충해 주세요', 10),
  flow('C002001', null, 'C001001', 'moisture', 'NEXT_STEP', '세안 다음 단계는 수분 충전이에요', 20),
  flow('C002002', null, 'C001001', 'moisture', 'NEXT_STEP', '지운 다음엔 수분 토너로 결부터 정돈하세요', 20),
  flow('C002003', 'exfoliate', 'C001001', 'soothe', 'BUFFER', '각질 케어 다음엔 진정 성분으로 완충하는 게 좋아요', 10),
  flow('C001001', 'exfoliate', 'C001002', 'soothe', 'BUFFER', '각질 토너 다음 단계는 진정 세럼으로 완충하세요', 10),
  flow('C001001', null, 'C001002', null, 'NEXT_STEP', '결을 정돈했다면 영양을 채울 차례예요', 20),
  flow('C001002', null, 'C001003', 'moisture', 'NEXT_STEP', '세럼의 수분을 크림으로 덮어 가두세요', 20),
  flow('C001003', null, 'C004001', 'uv', 'NEXT_STEP', '아침 루틴의 마지막은 자외선 차단이에요', 20),
  flow('C004001', null, 'C004003', 'soothe', 'NEXT_STEP', '햇빛을 본 날엔 진정 케어로 마무리하세요', 20),
  flow('C004001', null, 'C002002', 'cleanse', 'PAIRED_REMOVAL', '자외선차단제는 클렌징오일로 지워야 남지 않아요', 10),
  flow('C004002', null, 'C002002', 'cleanse', 'PAIRED_REMOVAL', '선스틱도 저녁엔 오일 클렌징으로 지워 주세요', 10),
  flow('C005002', null, 'C005003', null, 'NEXT_STEP', '면도 다음엔 진정 제품으로 마무리하세요', 20),
];

function concern(
  concernTagSlug: string,
  toCategoryCode: string,
  toTagSlug: string,
  reason: string,
  priority: number,
): ConcernRuleView {
  return { concernTagSlug, toCategoryCode, toTagSlug, reason, priority };
}

const CONCERN_RULES: ConcernRuleView[] = [
  concern('exfoliate', 'C002003', 'exfoliate', '각질이 고민이라면 주 1~2회 필링부터 시작하세요', 10),
  concern('exfoliate', 'C001001', 'soothe', '각질 케어 뒤엔 진정 토너로 완충해 주세요', 20),
  concern('sebum', 'C002001', 'sebum', '피지가 고민이라면 세안부터 피지 잡는 제품으로', 10),
  concern('sebum', 'C001001', 'sebum', '세안 뒤 유분 정돈까지 이어가세요', 20),
  concern('pore', 'C001002', 'pore', '모공은 세럼 단계에서 정면으로 잡는 게 효율적이에요', 10),
  concern('pore', 'C002001', 'pore', '모공 관리는 잘 씻어내는 것부터예요', 20),
  concern('trouble', 'C001002', 'trouble', '트러블이 고민이라면 세럼으로 집중 관리하세요', 10),
  concern('trouble', 'C002001', 'trouble', '트러블 피부일수록 세정 단계 선택이 중요해요', 20),
  concern('soothe', 'C001001', 'soothe', '예민한 날엔 진정 토너로 결부터 달래 주세요', 10),
  concern('soothe', 'C001002', 'soothe', '진정 성분 세럼으로 한 겹 더 얹어 보세요', 20),
  concern('moisture', 'C001003', 'moisture', '보습이 고민이라면 덮어 가두는 크림이 핵심이에요', 10),
  concern('moisture', 'C001002', 'moisture', '크림 전에 수분 세럼으로 채워 두세요', 20),
  concern('barrier', 'C001003', 'barrier', '장벽이 무너졌다면 크림으로 지붕부터 올리세요', 10),
  concern('barrier', 'C001002', 'barrier', '세라마이드 계열 세럼으로 장벽을 채워 보세요', 20),
  concern('bright', 'C001002', 'bright', '톤이 고민이라면 브라이트닝 세럼이 출발점이에요', 10),
  concern('bright', 'C004001', 'uv', '미백 관리의 절반은 자외선 차단이에요', 20),
  concern('anti-aging', 'C001002', 'anti-aging', '주름 관리는 세럼 단계에서 시작하세요', 10),
  concern('anti-aging', 'C001003', 'anti-aging', '고영양 크림으로 마무리하면 더 오래 갑니다', 20),
  concern('gentle', 'C001001', 'gentle', '자극 없는 토너로 결만 정돈해 주세요', 20),
];

// ── 체인 실행 ──────────────────────────────────────────────────────────────────────

/** goodsNo → 최악 verdict. null이면 게이트 없이 진행한다(설계 §3.3). */
type VerdictLookup = (base: number, candidates: number[]) => Map<number, string> | null;

const NO_GATE: VerdictLookup = () => null;

function prevPickOf(pick: GoodsListItem): PrevPick {
  const row = ROW_BY_GOODS_NO.get(pick.goodsNo);
  if (row === undefined) {
    throw new Error(`스냅샷에 없는 goodsNo: ${pick.goodsNo}`);
  }
  return { goodsNo: pick.goodsNo, cat3: toCat3(row.categoryCode), tags: pick.tags.map((t) => t.slug) };
}

function composeChain(signals: ComposerSignals, verdictsOf: VerdictLookup = NO_GATE): StepComposition[] {
  const compositions: StepComposition[] = [];
  let prevPick: PrevPick | null = null;

  for (const { step, items } of POOLS) {
    const composition = composeStep({
      step,
      candidates: items,
      signals,
      prevPick,
      flowRules: FLOW_RULES,
      concernRules: CONCERN_RULES,
      verdicts: prevPick === null ? null : verdictsOf(prevPick.goodsNo, items.map((p) => p.goodsNo)),
    });
    compositions.push(composition);
    prevPick = composition.pick === null ? null : prevPickOf(composition.pick);
  }
  return compositions;
}

/** 픽 + 대안 = 그 단계에서 사용자가 실제로 보는 4칸. */
function topFourOf(composition: StepComposition): number[] {
  return [composition.pick, ...composition.alternatives]
    .filter((p): p is GoodsListItem => p !== null)
    .map((p) => p.goodsNo);
}

// ── 프로필 20종 — 고민 0~3개 × 피부타입 4종 × 행동 패턴 3종을 결정적으로 조합한다 ────────

const CONCERN_SETS: string[][] = [
  [],
  ['moisture'],
  ['sebum', 'pore'],
  ['bright', 'anti-aging', 'soothe'],
];
const SKIN_TYPES: SkinType[] = ['DRY', 'OILY', 'COMBINATION', 'SENSITIVE'];
const TEXTURES = ['fresh', 'dewy', 'matte'];
const BEHAVIOR_PATTERNS = ['none', 'views', 'carts'] as const;
type BehaviorPattern = (typeof BEHAVIOR_PATTERNS)[number];

const PROFILE_COUNT = 20;

/**
 * 행동 이벤트를 결정적으로 만든다. 프로필 인덱스가 어떤 상품을 보는지를 바꾸므로
 * 같은 패턴이라도 프로필마다 다른 신호가 쌓인다.
 */
function behaviorEvents(profileIndex: number, pattern: BehaviorPattern, count: number): AffinityEvent[] {
  if (pattern === 'none') {
    return [];
  }
  const w = pattern === 'views' ? WEIGHT.view : WEIGHT.cart;
  const events: AffinityEvent[] = [];
  for (let n = 0; n < count; n += 1) {
    const pool = POOLS[(profileIndex + n) % POOLS.length];
    const row = pool.rows[(profileIndex * 3 + n * 5) % pool.rows.length];
    events.push({ goodsNo: row.goodsNo, cat3: toCat3(row.categoryCode), tags: row.tags, w });
  }
  return events;
}

interface SimProfile {
  index: number;
  skinType: SkinType;
  pattern: BehaviorPattern;
  events: AffinityEvent[];
  signals: ComposerSignals;
  hasSignal: boolean;
}

function profileAt(index: number, eventCount?: number): SimProfile {
  const rawConcerns = [...CONCERN_SETS[index % 4], TEXTURES[index % 3]];
  const skinType = SKIN_TYPES[Math.floor(index / 4) % 4];
  const pattern = BEHAVIOR_PATTERNS[index % 3];
  const count = eventCount ?? (pattern === 'views' ? 8 : 4);
  const events = behaviorEvents(index, pattern, count);
  const concerns = effectiveConcerns(rawConcerns, skinType);
  return {
    index,
    skinType,
    pattern,
    events,
    signals: { concerns, textures: preferredTextures(rawConcerns), affinity: aggregate(events) },
    // 피부타입 파생까지 끝난 뒤라야 "신호가 있다"가 정확해진다(profile.ts effectiveConcerns 주석).
    hasSignal: concerns.length > 0 || events.length > 0,
  };
}

const PROFILES = Array.from({ length: PROFILE_COUNT }, (_, i) => profileAt(i));

function jaccard(a: number[], b: number[]): number {
  const setA = new Set(a);
  const setB = new Set(b);
  const intersection = [...setA].filter((x) => setB.has(x)).length;
  const union = new Set([...a, ...b]).size;
  return union === 0 ? 1 : intersection / union;
}

describe('시뮬레이션 — 설계 §6 성과 측정', () => {
  it('[성과] 사용자 간 구성 차별화', () => {
    const chains = PROFILES.map((p) => composeChain(p.signals));

    const similarities: number[] = [];
    for (let a = 0; a < chains.length; a += 1) {
      for (let b = a + 1; b < chains.length; b += 1) {
        for (let s = 0; s < ROUTINE_STEPS.length; s += 1) {
          similarities.push(jaccard(topFourOf(chains[a][s]), topFourOf(chains[b][s])));
        }
      }
    }
    const mean = similarities.reduce((sum, x) => sum + x, 0) / similarities.length;

    // 기준선(전원 인기순)은 정확히 1.0이다 — 같은 풀에서 같은 상위 4를 보므로.
    const baseline = jaccard(topFourOf(chains[0][0]), topFourOf(chains[0][0]));

    console.log(
      `[성과] 사용자 간 구성 차별화 — 프로필 ${PROFILES.length}종, 쌍×단계 ${similarities.length}건, ` +
        `평균 자카드 유사도 ${mean.toFixed(4)} (기준선 ${baseline.toFixed(2)})`,
    );

    expect(baseline).toBe(1);
    expect(mean).toBeLessThan(0.85);
  });

  it('[성과] 개인화 커버리지', () => {
    const signalled = PROFILES.filter((p) => p.hasSignal);
    let steps = 0;
    let differs = 0;

    for (const profile of signalled) {
      const chain = composeChain(profile.signals);
      chain.forEach((composition, s) => {
        steps += 1;
        // 기준선 = 그 단계 풀의 인기 1위(POOLS[s].items[0]).
        if (composition.pick !== null && composition.pick.goodsNo !== POOLS[s].items[0].goodsNo) {
          differs += 1;
        }
      });
    }
    const coverage = differs / steps;

    console.log(
      `[성과] 개인화 커버리지 — 신호 있는 프로필 ${signalled.length}종 × ${ROUTINE_STEPS.length}단계 = ${steps}단계 중 ` +
        `픽 ≠ 인기 1위 ${differs}단계, 비율 ${coverage.toFixed(4)}`,
    );

    expect(steps).toBe(signalled.length * ROUTINE_STEPS.length);
    expect(coverage).toBeGreaterThanOrEqual(0.4);
  });

  it('[성과] 연속성', () => {
    // 한 프로필에 이벤트를 0→10개 하나씩 추가한다. 티어 사다리가 없으므로 어느 경계에서도
    // 화면이 통째로 갈리면 안 된다 — 특히 옛 티어2 임계였던 4→5개 경계(문제 #3).
    const base = profileAt(7, 0);
    const chains = Array.from({ length: 11 }, (_, count) => {
      const events = behaviorEvents(7, 'carts', count);
      return composeChain({ ...base.signals, affinity: aggregate(events) });
    });

    const churn = chains.slice(1).map((chain, i) => {
      const before = chains[i];
      return chain.filter((c, s) => c.pick?.goodsNo !== before[s].pick?.goodsNo).length;
    });
    const worst = Math.max(...churn);

    console.log(
      `[성과] 연속성 — 이벤트 0→10개, 인접 구성 간 바뀐 픽 수 [${churn.join(', ')}] (5단계 중), ` +
        `최대 ${worst}, 4→5 경계 ${churn[4]}`,
    );

    expect(churn).toHaveLength(10);
    expect(worst).toBeLessThanOrEqual(3);
  });

  it('[성과] 강도 반영', () => {
    // 세럼 단계에서, 인기 상위가 아닌 상품을 1→5회 담는다. 다른 상품(고정 5회)이 정규화
    // 분모를 잡고 있으므로 정규화 친화도가 실제로 자란다 — 순위가 내려가면 문제 #2의 재발이다.
    const serum = POOLS[2];
    const target = serum.rows[7];
    const anchor = serum.rows[1];
    const cartOf = (row: SnapshotGoods): AffinityEvent => ({
      goodsNo: row.goodsNo,
      cat3: toCat3(row.categoryCode),
      tags: row.tags,
      w: WEIGHT.cart,
    });

    const ranks = [1, 2, 3, 4, 5].map((repeat) => {
      const events = [
        ...Array.from({ length: 5 }, () => cartOf(anchor)),
        ...Array.from({ length: repeat }, () => cartOf(target)),
      ];
      const composition = composeStep({
        step: serum.step,
        candidates: serum.items,
        signals: { concerns: [], textures: [], affinity: aggregate(events) },
        prevPick: null,
        flowRules: FLOW_RULES,
        concernRules: CONCERN_RULES,
        verdicts: null,
      });
      const seen = topFourOf(composition).indexOf(target.goodsNo);
      // 상위 4칸 밖이면 "가장 낮은 순위"로 센다 — 안으로 들어오는 것도 상승이다.
      return seen === -1 ? ALTERNATIVE_COUNT + 1 : seen;
    });

    console.log(
      `[성과] 강도 반영 — 세럼 goodsNo ${target.goodsNo}(인기 8위) 담기 1→5회, ` +
        `상위 4칸 내 순위 [${ranks.join(', ')}] (${ALTERNATIVE_COUNT + 1}=권외)`,
    );

    expect(ranks).toEqual([...ranks].sort((a, b) => b - a));
  });

  it('[성과] 궁합 0건', () => {
    // 게이트 없이 나온 인접 픽 쌍을 그대로 CONFLICT 픽스처로 삼는다 — 조합기가 피해야 할
    // 조합을 실제로 피하는지 보는 가장 빡빡한 픽스처다(무작위 픽스처는 안 걸릴 수 있다).
    const conflicts = new Set<string>();
    const pairKey = (base: number, candidate: number) => `${base}→${candidate}`;

    for (const profile of PROFILES) {
      const chain = composeChain(profile.signals);
      for (let s = 1; s < chain.length; s += 1) {
        const base = chain[s - 1].pick;
        const next = chain[s].pick;
        if (base !== null && next !== null) {
          conflicts.add(pairKey(base.goodsNo, next.goodsNo));
        }
      }
    }

    const gate: VerdictLookup = (base, candidates) =>
      new Map(
        candidates
          .filter((goodsNo) => conflicts.has(pairKey(base, goodsNo)))
          .map((goodsNo) => [goodsNo, 'CONFLICT']),
      );

    let checked = 0;
    for (const profile of PROFILES) {
      const chain = composeChain(profile.signals, gate);
      for (let s = 1; s < chain.length; s += 1) {
        const base = chain[s - 1].pick;
        const next = chain[s].pick;
        if (base !== null && next !== null) {
          checked += 1;
          expect(conflicts.has(pairKey(base.goodsNo, next.goodsNo))).toBe(false);
        }
      }
    }

    console.log(
      `[성과] 궁합 0건 — CONFLICT 픽스처 ${conflicts.size}쌍, 프로필 ${PROFILES.length}종 × 인접 4쌍 = ` +
        `${checked}쌍 검사, 충돌 0건`,
    );

    expect(checked).toBe(PROFILES.length * (ROUTINE_STEPS.length - 1));
  });
});

import { describe, expect, it } from 'vitest';
import type { GoodsListItem, TagView } from '../../types/goods';
import type { ConcernRuleView, FlowRuleView } from '../../types/routine';
import type { RoutineStep } from '../routine/steps';
import type { AffinityEvent } from './events';
import { WEIGHT } from './events';
import { aggregate } from './profile';
import type { ComposerSignals, PrevPick } from './composer';
import { composeStep, pickConcernReason, pickFlowRule } from './composer';

/** 태그 kind는 조합기가 보지 않는다(slug만 쓴다) — 픽스처는 EFFECT로 고정한다. */
function tagOf(slug: string): TagView {
  return { name: slug, kind: 'EFFECT', slug };
}

function goods(goodsNo: number, tags: string[]): GoodsListItem {
  return {
    goodsNo,
    brandName: `브랜드${goodsNo}`,
    name: `상품${goodsNo}`,
    thumbnailUrl: `/images/goods/${goodsNo}.jpg`,
    listPrice: 20000,
    salePrice: 18000,
    discountRate: 10,
    badges: [],
    rating: 4.5,
    reviewCount: 10,
    wished: false,
    todayDreamAvailable: false,
    tags: tags.map(tagOf),
  };
}

function stepOf(categoryCode: string): RoutineStep {
  return {
    id: 'step',
    order: 1,
    label: '단계',
    categoryCode,
    copy: '설명',
    image: '/images/routine/01-cleansing.svg',
  };
}

function signalsOf(partial: Partial<ComposerSignals> = {}): ComposerSignals {
  return { concerns: [], textures: [], affinity: new Map(), ...partial };
}

function flowRule(partial: Partial<FlowRuleView> = {}): FlowRuleView {
  return {
    fromCategoryCode: 'C001001',
    fromTagSlug: null,
    toCategoryCode: 'C001002',
    toTagSlug: 'moisture',
    edgeKind: 'NEXT_STEP',
    reason: '결을 정돈했다면 영양을 채울 차례예요',
    priority: 20,
    ...partial,
  };
}

function concernRule(partial: Partial<ConcernRuleView> = {}): ConcernRuleView {
  return {
    concernTagSlug: 'moisture',
    toCategoryCode: 'C001002',
    toTagSlug: 'moisture',
    reason: '크림 전에 수분 세럼으로 채워 두세요',
    priority: 20,
    ...partial,
  };
}

/** 기본 인자 — 테스트마다 바꾸는 축만 override 한다. */
function inputOf(partial: Partial<Parameters<typeof composeStep>[0]> = {}) {
  return {
    step: stepOf('C001002'),
    candidates: [] as GoodsListItem[],
    signals: signalsOf(),
    prevPick: null as PrevPick | null,
    flowRules: [] as FlowRuleView[],
    concernRules: [] as ConcernRuleView[],
    verdicts: null as Map<number, string> | null,
    ...partial,
  };
}

function goodsNosOf(composition: ReturnType<typeof composeStep>): number[] {
  return [composition.pick, ...composition.alternatives]
    .filter((p): p is GoodsListItem => p !== null)
    .map((p) => p.goodsNo);
}

describe('composeStep — 점수 공식', () => {
  it('콜드스타트: 신호가 전무하면 픽이 서버 인기 1위다', () => {
    const candidates = [goods(1, ['soothe']), goods(2, ['moisture']), goods(3, ['bright'])];

    const result = composeStep(inputOf({ candidates }));

    // popularityPrior 항만 남아 서버 인기순이 그대로 살아난다(설계 §2 콜드스타트 수렴).
    expect(result.pick?.goodsNo).toBe(1);
    expect(result.alternatives.map((p) => p.goodsNo)).toEqual([2, 3]);
    expect(result.reason).toBeNull();
    expect(result.matched).toEqual({ concerns: [], behaviors: [] });
  });

  it('고민 1개 일치가 사용감+인기 합을 이긴다', () => {
    // g1 = 사용감(0.5) + 인기 1위(0.3) = 0.8 / g2 = 고민 1개(2.0) + 인기 2위(0.15) = 2.15
    const candidates = [goods(1, ['fresh']), goods(2, ['moisture'])];

    const result = composeStep(
      inputOf({
        candidates,
        signals: signalsOf({ concerns: ['moisture'], textures: ['fresh'] }),
      }),
    );

    expect(result.pick?.goodsNo).toBe(2);
    expect(result.matched.concerns).toEqual(['moisture']);
  });

  it('고민 일치 태그가 3개여도 2개로 캡된다', () => {
    // g1 고민 3개: 캡(2) → 4.0 + 0.3 = 4.3, 캡이 없으면 6.0 + 0.3 = 6.3
    // g2 고민 2개 + 행동 최대: 4.0 + 1.5 + 0.15 = 5.65
    // 캡이 살아 있어야 g2가 이긴다.
    const candidates = [goods(1, ['moisture', 'soothe', 'barrier']), goods(2, ['pore', 'barrier'])];

    const result = composeStep(
      inputOf({
        candidates,
        signals: signalsOf({
          concerns: ['moisture', 'soothe', 'barrier', 'pore'],
          affinity: new Map([['C001002|pore', 3]]),
        }),
      }),
    );

    expect(result.pick?.goodsNo).toBe(2);
  });

  it('행동 친화도는 후보 중 최대값으로 정규화된다 — 최대가 0이면 전원 0', () => {
    // 정규화: g2가 최대(10)라 1.5를 다 받고, g1은 1/10만 받아 인기 1위 프리미엄을 못 이긴다.
    const candidates = [goods(1, ['soothe']), goods(2, ['moisture'])];
    const normalized = composeStep(
      inputOf({
        candidates,
        signals: signalsOf({
          affinity: new Map([
            ['C001002|soothe', 1],
            ['C001002|moisture', 10],
          ]),
        }),
      }),
    );

    expect(normalized.pick?.goodsNo).toBe(2);

    // 최대가 0(=신호 전무)이면 0으로 나누지 않고 전원 0 — 인기순이 그대로 남는다.
    const noSignal = composeStep(inputOf({ candidates }));

    expect(goodsNosOf(noSignal)).toEqual([1, 2]);
    expect(noSignal.matched.behaviors).toEqual([]);
  });

  it('대분류 단계(클렌징 C002)에서도 중분류 키(C002001|tag)의 이벤트가 친화도에 합산된다', () => {
    // 이벤트 키는 중분류 7자인데 클렌징 단계의 categoryCode는 대분류 4자다.
    // Map.get('C002|exfoliate') 직접 조회로 되돌리면 친화도가 0이 되어 g1이 픽이 된다.
    const candidates = [goods(1, ['cleanse']), goods(2, ['exfoliate'])];

    const result = composeStep(
      inputOf({
        step: stepOf('C002'),
        candidates,
        signals: signalsOf({ affinity: new Map([['C002001|exfoliate', 3]]) }),
      }),
    );

    expect(result.pick?.goodsNo).toBe(2);
    expect(result.matched.behaviors).toEqual(['exfoliate']);
  });

  it('같은 상품을 담을수록(w:3 반복) 관련 태그 후보의 점수가 단조 증가한다', () => {
    // g4(sebum, 담기 5회=15)가 정규화 분모를 고정한다. g3(moisture)의 담기를 1→5회 늘리면
    // 정규화 친화도가 0.2씩 올라 순위가 내려가지 않고, 5회째에 g4를 추월한다.
    const candidates = [
      goods(1, ['gentle']),
      goods(2, ['pore']),
      goods(3, ['moisture']),
      goods(4, ['sebum']),
    ];
    const cartOf = (goodsNo: number, tags: string[]): AffinityEvent => ({
      goodsNo,
      cat3: 'C001002',
      tags,
      w: WEIGHT.cart,
    });
    const anchor = Array.from({ length: 5 }, () => cartOf(4, ['sebum']));

    const ranks = [1, 2, 3, 4, 5].map((repeat) => {
      const events = [...anchor, ...Array.from({ length: repeat }, () => cartOf(3, ['moisture']))];
      const result = composeStep(
        inputOf({
          candidates,
          signals: signalsOf({ concerns: ['pore'], affinity: aggregate(events) }),
        }),
      );
      return goodsNosOf(result).indexOf(3);
    });

    expect(ranks).toEqual([...ranks].sort((a, b) => b - a)); // 단조 비증가
    expect(ranks[4]).toBeLessThan(ranks[0]); // 실제로 올라간다 — 상수열이 아니다
  });

  it('행동(1.5)+흐름(1.0)이 고민 1개(2.0)를 뒤집는다', () => {
    // g1 = 고민 1개(2.0) + 인기 1위(0.3) = 2.3
    // g2 = 행동 최대(1.5) + 흐름(1.0) + 인기 2위(0.15) = 2.65
    const candidates = [goods(1, ['moisture']), goods(2, ['soothe'])];

    const result = composeStep(
      inputOf({
        candidates,
        signals: signalsOf({
          concerns: ['moisture'],
          affinity: new Map([['C001002|soothe', 3]]),
        }),
        prevPick: { goodsNo: 99, cat3: 'C001001', tags: ['exfoliate'] },
        flowRules: [
          flowRule({
            fromCategoryCode: 'C001001',
            fromTagSlug: 'exfoliate',
            toCategoryCode: 'C001002',
            toTagSlug: 'soothe',
            edgeKind: 'BUFFER',
            reason: '각질 토너 다음 단계는 진정 세럼으로 완충하세요',
            priority: 10,
          }),
        ],
      }),
    );

    expect(result.pick?.goodsNo).toBe(2);
  });
});

describe('composeStep — 궁합 게이트', () => {
  const candidates = [goods(1, ['moisture']), goods(2, ['soothe']), goods(3, ['bright'])];

  it('이전 픽과 CONFLICT인 후보는 점수와 무관하게 제거된다', () => {
    const result = composeStep(
      inputOf({
        candidates,
        // g1은 고민 일치 + 인기 1위로 최고점이지만 CONFLICT라 후보에서 사라진다.
        signals: signalsOf({ concerns: ['moisture'] }),
        verdicts: new Map([[1, 'CONFLICT']]),
      }),
    );

    expect(goodsNosOf(result)).toEqual([2, 3]);
  });

  it('verdicts가 null이면(게이트 실패) 전원 통과한다', () => {
    const result = composeStep(inputOf({ candidates, verdicts: null }));

    expect(goodsNosOf(result)).toEqual([1, 2, 3]);
  });

  it('게이트로 전원 탈락하면 pick이 null이다', () => {
    const result = composeStep(
      inputOf({
        candidates,
        verdicts: new Map([
          [1, 'CONFLICT'],
          [2, 'CONFLICT'],
          [3, 'CONFLICT'],
        ]),
      }),
    );

    expect(result).toEqual({
      pick: null,
      alternatives: [],
      reason: null,
      matched: { concerns: [], behaviors: [] },
    });
  });
});

describe('pickFlowRule — 전이 규칙 선택', () => {
  const prevPick: PrevPick = { goodsNo: 1, cat3: 'C001001', tags: ['exfoliate'] };

  it('pickFlowRule: BUFFER가 NEXT_STEP보다 먼저다 — priority가 높아도', () => {
    const nextStep = flowRule({ edgeKind: 'NEXT_STEP', priority: 1, reason: '다음 단계' });
    const buffer = flowRule({ edgeKind: 'BUFFER', priority: 99, reason: '완충' });

    expect(pickFlowRule(prevPick, stepOf('C001002'), [nextStep, buffer])).toBe(buffer);
  });

  it('pickFlowRule: from_tag가 null인 규칙은 픽 태그와 무관하게 매칭된다', () => {
    const rule = flowRule({ fromTagSlug: null });
    const unrelated: PrevPick = { goodsNo: 2, cat3: 'C001001', tags: ['아무-태그'] };

    expect(pickFlowRule(unrelated, stepOf('C001002'), [rule])).toBe(rule);
    // from_tag가 있으면 그 태그를 가진 픽만 매칭된다 — null의 의미가 "무관"임을 대비로 고정한다.
    expect(pickFlowRule(unrelated, stepOf('C001002'), [flowRule({ fromTagSlug: 'exfoliate' })])).toBeNull();
  });

  it('pickFlowRule: 클렌징(대분류 4자) 단계도 to_category 7자와 매칭된다', () => {
    // 규칙의 to는 7자(C002002), 단계는 4자(C002) — startsWith 방향이 뒤집히면 null이 된다.
    const rule = flowRule({
      fromCategoryCode: 'C004001',
      fromTagSlug: null,
      toCategoryCode: 'C002002',
      toTagSlug: 'cleanse',
      edgeKind: 'PAIRED_REMOVAL',
      reason: '자외선차단제는 클렌징오일로 지워야 남지 않아요',
      priority: 10,
    });
    const sunPick: PrevPick = { goodsNo: 21, cat3: 'C004001', tags: ['uv'] };

    expect(pickFlowRule(sunPick, stepOf('C002'), [rule])).toBe(rule);
  });
});

describe('composeStep — 흐름과 근거', () => {
  it('flowTag 일치 후보에 flow 가중치가 붙어 순위가 뒤집힌다', () => {
    // g1 = 인기 1위(0.3) / g2 = 흐름(1.0) + 인기 2위(0.15) = 1.15
    const candidates = [goods(1, ['bright']), goods(2, ['moisture'])];
    const rules = [flowRule({ fromTagSlug: null, toTagSlug: 'moisture' })];
    const prevPick: PrevPick = { goodsNo: 9, cat3: 'C001001', tags: ['soothe'] };

    expect(composeStep(inputOf({ candidates })).pick?.goodsNo).toBe(1);
    expect(
      composeStep(inputOf({ candidates, prevPick, flowRules: rules })).pick?.goodsNo,
    ).toBe(2);
  });

  it('reason 사다리: 전이 규칙 → 고민 규칙 → null 순으로 떨어진다', () => {
    const candidates = [goods(1, ['moisture'])];
    const prevPick: PrevPick = { goodsNo: 9, cat3: 'C001001', tags: ['exfoliate'] };
    const rules = [flowRule({ reason: '결을 정돈했다면 영양을 채울 차례예요' })];
    const concerns = [concernRule({ reason: '크림 전에 수분 세럼으로 채워 두세요' })];
    const signals = signalsOf({ concerns: ['moisture'] });

    expect(
      composeStep(inputOf({ candidates, signals, prevPick, flowRules: rules, concernRules: concerns }))
        .reason,
    ).toBe('결을 정돈했다면 영양을 채울 차례예요');
    expect(
      composeStep(inputOf({ candidates, signals, prevPick, flowRules: [], concernRules: concerns }))
        .reason,
    ).toBe('크림 전에 수분 세럼으로 채워 두세요');
    expect(
      composeStep(inputOf({ candidates, signals, prevPick, flowRules: [], concernRules: [] })).reason,
    ).toBeNull();
  });

  it('pickConcernReason: 고민 선택 순서가 priority보다 먼저다', () => {
    const rules = [
      concernRule({ concernTagSlug: 'moisture', priority: 10, reason: '수분 규칙' }),
      concernRule({ concernTagSlug: 'bright', priority: 1, reason: '미백 규칙' }),
    ];

    expect(pickConcernReason(stepOf('C001002'), ['bright', 'moisture'], rules)).toBe('미백 규칙');
    expect(pickConcernReason(stepOf('C001002'), ['moisture', 'bright'], rules)).toBe('수분 규칙');
  });
});

describe('composeStep — 결정성과 대안', () => {
  it('동점이면 서버 인기순, 그다음 goodsNo — 두 번 실행해도 같은 결과다', () => {
    // g1 = 0 + 0.3×(3/3) = 0.3 / g2 = 1.5×(1/15) + 0.3×(2/3) = 0.3 — 정확히 동점이다.
    // g3(친화도 15)이 정규화 분모를 고정한다. 동점은 서버 인기순(i)으로 깨져 g1이 앞선다.
    const candidates = [goods(11, ['gentle']), goods(22, ['soothe']), goods(33, ['sebum'])];
    const input = inputOf({
      candidates,
      signals: signalsOf({
        affinity: new Map([
          ['C001002|soothe', 1],
          ['C001002|sebum', 15],
        ]),
      }),
    });

    const first = composeStep(input);
    const second = composeStep(input);

    expect(goodsNosOf(first)).toEqual([33, 11, 22]);
    expect(goodsNosOf(second)).toEqual(goodsNosOf(first));
  });

  it('대안은 픽을 제외한 점수 2~4위다', () => {
    const candidates = [
      goods(1, []),
      goods(2, []),
      goods(3, []),
      goods(4, []),
      goods(5, ['moisture']),
    ];

    const result = composeStep(
      inputOf({ candidates, signals: signalsOf({ concerns: ['moisture'] }) }),
    );

    // g5가 고민 일치로 1위, 나머지는 인기순 그대로 2~4위. 5위(g4)는 잘린다.
    expect(result.pick?.goodsNo).toBe(5);
    expect(result.alternatives.map((p) => p.goodsNo)).toEqual([1, 2, 3]);
  });
});

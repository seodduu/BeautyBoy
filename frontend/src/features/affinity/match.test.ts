import { describe, expect, it } from 'vitest';
import type { ConcernRuleView, FlowRuleView } from '../../types/routine';
import type { GoodsListItem, TagView } from '../../types/goods';
import type { AffinityEvent } from './events';
import { WEIGHT } from './events';
import { matchByBehavior, matchByProfile, rankByTexture } from './match';

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
    toCategoryCode: 'C001003',
    toTagSlug: 'moisture',
    reason: '보습이 고민이라면 덮어 가두는 크림이 핵심이에요',
    priority: 10,
    ...partial,
  };
}

function viewOf(cat3: string, tags: string[]): AffinityEvent {
  return { goodsNo: 1, cat3, tags, w: WEIGHT.view };
}

describe('matchByBehavior — 티어2', () => {
  it('from 태그가 null인 규칙은 태그와 무관하게 매칭된다', () => {
    const events = [viewOf('C001001', ['아무-태그'])];

    const targets = matchByBehavior(events, [flowRule({ fromTagSlug: null })], []);

    expect(targets).toEqual([
      {
        stepId: 'serum',
        tag: 'moisture',
        reason: '결을 정돈했다면 영양을 채울 차례예요',
      },
    ]);
  });

  it('from 태그가 있으면 그 태그를 가진 이벤트만 점수를 만든다', () => {
    const events = [viewOf('C001001', ['moisture'])];

    expect(matchByBehavior(events, [flowRule({ fromTagSlug: 'exfoliate' })], [])).toEqual([]);
  });

  it('from 카테고리가 안 맞으면 점수 0이라 후보에서 빠진다', () => {
    const events = [viewOf('C002001', ['cleanse'])];

    expect(matchByBehavior(events, [flowRule({ fromCategoryCode: 'C001001' })], [])).toEqual([]);
  });

  it('같은 상품에 BUFFER(10)와 NEXT_STEP(20)이 걸리면 점수가 낮아도 BUFFER만 남는다', () => {
    // 두 규칙 모두 세럼(C001002)을 겨냥한다 — 한 STEP은 하나만 차지할 수 있다.
    const buffer = flowRule({
      fromTagSlug: 'exfoliate',
      toTagSlug: 'soothe',
      edgeKind: 'BUFFER',
      priority: 10,
      reason: '각질 토너 다음 단계는 진정 세럼으로 완충하세요',
    });
    const nextStep = flowRule({ fromTagSlug: null, toTagSlug: 'moisture', priority: 20 });
    // BUFFER 점수 1 < NEXT_STEP 점수 4 — 그래도 완충이 이긴다는 것이 규칙의 의도다.
    const events = [
      viewOf('C001001', ['exfoliate']),
      viewOf('C001001', ['moisture']),
      viewOf('C001001', ['moisture']),
      viewOf('C001001', ['moisture']),
    ];

    const targets = matchByBehavior(events, [nextStep, buffer], []);

    expect(targets).toHaveLength(1);
    expect(targets[0].tag).toBe('soothe');
  });

  it('toTagSlug가 고민에 있으면 점수가 1.5배가 되어 같은 priority 안에서 순위가 뒤집힌다', () => {
    const soothe = flowRule({
      fromTagSlug: 'exfoliate',
      toCategoryCode: 'C001002',
      toTagSlug: 'soothe',
      priority: 20,
    });
    const barrier = flowRule({
      fromTagSlug: null,
      toCategoryCode: 'C001003',
      toTagSlug: 'barrier',
      priority: 20,
    });
    // soothe 규칙 3점(exfoliate 3건) < barrier 규칙 4점(전체 4건).
    const events = [
      viewOf('C001001', ['exfoliate']),
      viewOf('C001001', ['exfoliate']),
      viewOf('C001001', ['exfoliate']),
      viewOf('C001001', ['moisture']),
    ];

    const plain = matchByBehavior(events, [soothe, barrier], []);
    expect(plain[0].tag).toBe('barrier');

    // 고민에 soothe가 있으면 3 × 1.5 = 4.5 > 4 로 뒤집힌다.
    const boosted = matchByBehavior(events, [soothe, barrier], ['soothe']);
    expect(boosted[0].tag).toBe('soothe');
  });

  it('목표가 3개 이상 나와도 2섹션만 반환한다', () => {
    const rules = [
      flowRule({ toCategoryCode: 'C001002', priority: 10 }),
      flowRule({ toCategoryCode: 'C001003', priority: 20 }),
      flowRule({ toCategoryCode: 'C004001', priority: 30 }),
    ];

    const targets = matchByBehavior([viewOf('C001001', ['moisture'])], rules, []);

    expect(targets.map((t) => t.stepId)).toEqual(['serum', 'cream']);
  });

  it('같은 STEP을 겨냥한 규칙이 둘이면 앞선 것 하나만 남는다', () => {
    const rules = [
      flowRule({ toTagSlug: 'moisture', priority: 20 }),
      flowRule({ toTagSlug: 'soothe', priority: 10 }),
    ];

    const targets = matchByBehavior([viewOf('C001001', ['moisture'])], rules, []);

    expect(targets).toHaveLength(1);
    expect(targets[0].tag).toBe('soothe');
  });

  it('to_category가 ROUTINE_STEPS에 없으면 그 규칙은 건너뛰고 다음 규칙이 자리를 채운다', () => {
    const rules = [
      // C003001(샴푸)은 루틴 5단계 밖이다 — 매핑할 STEP이 없다.
      flowRule({ toCategoryCode: 'C003001', toTagSlug: 'scalp', priority: 10 }),
      flowRule({ toCategoryCode: 'C001002', toTagSlug: 'moisture', priority: 20 }),
    ];

    const targets = matchByBehavior([viewOf('C001001', ['moisture'])], rules, []);

    expect(targets).toHaveLength(1);
    expect(targets[0].stepId).toBe('serum');
  });

  it('toTagSlug가 null인 규칙은 tag 없이 이유 문장만 실어 보낸다', () => {
    const targets = matchByBehavior(
      [viewOf('C001001', ['moisture'])],
      [flowRule({ toTagSlug: null })],
      [],
    );

    expect(targets[0].tag).toBeNull();
    expect(targets[0].reason).toBe('결을 정돈했다면 영양을 채울 차례예요');
  });

  it('이벤트가 없으면 빈 배열이다', () => {
    expect(matchByBehavior([], [flowRule()], ['moisture'])).toEqual([]);
  });
});

describe('matchByProfile — 티어1', () => {
  it('고민 2개가 서로 다른 STEP을 겨냥하면 둘 다 반환한다', () => {
    const rules = [
      concernRule({ concernTagSlug: 'moisture', toCategoryCode: 'C001003' }),
      concernRule({ concernTagSlug: 'trouble', toCategoryCode: 'C001002', toTagSlug: 'trouble' }),
    ];

    const targets = matchByProfile(['moisture', 'trouble'], rules);

    expect(targets.map((t) => t.stepId)).toEqual(['cream', 'serum']);
  });

  it('고민 목록이 비면 빈 배열을 반환한다', () => {
    expect(matchByProfile([], [concernRule()])).toEqual([]);
  });

  it('고민 선택 순서가 우선순위다 — 먼저 고른 고민의 규칙이 STEP을 차지한다', () => {
    // 같은 STEP(세럼)을 두 고민이 겨냥한다. trouble 쪽 priority가 더 낮지만(10 < 20)
    // 고민 선택 순서에서 bright가 앞서므로 bright가 자리를 차지한다.
    const rules = [
      concernRule({
        concernTagSlug: 'bright',
        toCategoryCode: 'C001002',
        toTagSlug: 'bright',
        priority: 20,
      }),
      concernRule({
        concernTagSlug: 'trouble',
        toCategoryCode: 'C001002',
        toTagSlug: 'trouble',
        priority: 10,
      }),
    ];

    const targets = matchByProfile(['bright', 'trouble'], rules);

    expect(targets).toHaveLength(1);
    expect(targets[0].tag).toBe('bright');
  });

  it('한 고민의 규칙이 여럿이면 priority 오름차순으로 훑는다', () => {
    const rules = [
      concernRule({ concernTagSlug: 'sebum', toCategoryCode: 'C001001', priority: 20 }),
      concernRule({ concernTagSlug: 'sebum', toCategoryCode: 'C002001', priority: 10 }),
    ];

    const targets = matchByProfile(['sebum'], rules);

    expect(targets.map((t) => t.stepId)).toEqual(['cleansing', 'toner']);
  });

  it('규칙이 없는 고민은 조용히 건너뛴다', () => {
    expect(matchByProfile(['anti-aging'], [concernRule({ concernTagSlug: 'moisture' })])).toEqual(
      [],
    );
  });
});

describe('rankByTexture — 사용감 tie-break', () => {
  function goods(goodsNo: number, slugs: string[]): GoodsListItem {
    const tags: TagView[] = slugs.map((slug) => ({ name: slug, kind: 'TEXTURE', slug }));
    return {
      goodsNo,
      brandName: '브랜드',
      name: `상품 ${goodsNo}`,
      thumbnailUrl: '',
      listPrice: 10000,
      salePrice: 10000,
      discountRate: 0,
      badges: [],
      rating: 0,
      reviewCount: 0,
      wished: false,
      todayDreamAvailable: false,
      tags,
    };
  }

  it('선호 사용감을 가진 후보가 앞으로 온다', () => {
    const items = [goods(1, []), goods(2, ['dewy']), goods(3, [])];

    expect(rankByTexture(items, ['dewy']).map((i) => i.goodsNo)).toEqual([2, 1, 3]);
  });

  it('일치 개수가 같으면 서버가 준 인기순을 유지한다 — 안정 정렬', () => {
    const items = [goods(1, ['dewy']), goods(2, ['dewy']), goods(3, [])];

    expect(rankByTexture(items, ['dewy']).map((i) => i.goodsNo)).toEqual([1, 2, 3]);
  });

  it('일치 개수가 많을수록 더 앞에 온다', () => {
    const items = [goods(1, ['dewy']), goods(2, ['dewy', 'fresh']), goods(3, [])];

    expect(rankByTexture(items, ['dewy', 'fresh']).map((i) => i.goodsNo)).toEqual([2, 1, 3]);
  });

  it('선호 사용감이 없으면 서버 순서를 그대로 돌려준다', () => {
    const items = [goods(1, []), goods(2, ['dewy'])];

    expect(rankByTexture(items, []).map((i) => i.goodsNo)).toEqual([1, 2]);
  });

  it('원본 배열을 건드리지 않는다', () => {
    const items = [goods(1, []), goods(2, ['dewy'])];

    rankByTexture(items, ['dewy']);

    expect(items.map((i) => i.goodsNo)).toEqual([1, 2]);
  });
});

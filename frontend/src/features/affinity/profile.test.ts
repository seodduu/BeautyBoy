import { describe, expect, it } from 'vitest';
import type { AffinityEvent } from './events';
import { WEIGHT } from './events';
import { aggregate, effectiveConcerns, preferredTextures, tierOf } from './profile';

function event(partial: Partial<AffinityEvent> = {}): AffinityEvent {
  return { goodsNo: 1, cat3: 'C001002', tags: ['moisture'], w: WEIGHT.view, ...partial };
}

describe('aggregate — (cat3 × tag) 점수 집계', () => {
  it('조회 3회(3점)와 담기 1회(3점)의 점수가 같다 — 가중치 1·2·3의 의도', () => {
    const views = aggregate([event(), event(), event()]);
    const cart = aggregate([event({ w: WEIGHT.cart })]);

    expect(views.get('C001002|moisture')).toBe(3);
    expect(cart.get('C001002|moisture')).toBe(3);
  });

  it('태그가 여러 개면 태그마다 같은 가중치가 더해진다', () => {
    const scores = aggregate([event({ tags: ['moisture', 'soothe'], w: WEIGHT.wish })]);

    expect(scores.get('C001002|moisture')).toBe(2);
    expect(scores.get('C001002|soothe')).toBe(2);
  });

  it('같은 태그라도 카테고리가 다르면 다른 키다 — 단계 축이 무너지지 않아야 한다', () => {
    const scores = aggregate([
      event({ cat3: 'C001002', tags: ['exfoliate'] }),
      event({ cat3: 'C002001', tags: ['exfoliate'] }),
    ]);

    expect(scores.get('C001002|exfoliate')).toBe(1);
    expect(scores.get('C002001|exfoliate')).toBe(1);
  });

  it('태그 없는 이벤트는 점수를 만들지 않는다', () => {
    expect(aggregate([event({ tags: [] })]).size).toBe(0);
  });

  it('이벤트가 없으면 빈 맵이다', () => {
    expect(aggregate([]).size).toBe(0);
  });
});

describe('tierOf — 3단 사다리', () => {
  const four = [event(), event(), event(), event()];
  const five = [...four, event()];

  it('이벤트 4개면 티어1로 판정한다 — 프로필이 있을 때', () => {
    expect(tierOf(four, ['moisture'])).toBe(1);
  });

  it('이벤트 5개면 티어2로 판정한다', () => {
    expect(tierOf(five, ['moisture'])).toBe(2);
  });

  it('프로필이 비어도 이벤트가 5개면 티어2다 — 행동이 프로필 없이도 성립한다', () => {
    expect(tierOf(five, [])).toBe(2);
  });

  it('프로필도 행동도 없으면 티어0이다', () => {
    expect(tierOf([], [])).toBe(0);
  });

  it('행동이 4개 이하이고 프로필도 비면 티어0이다', () => {
    expect(tierOf(four, [])).toBe(0);
  });
});

describe('effectiveConcerns — 직접 고른 것이 추론한 것을 이긴다', () => {
  it('고민이 하나라도 있으면 피부타입 파생 태그가 쓰이지 않는다', () => {
    expect(effectiveConcerns(['trouble'], 'DRY')).toEqual(['trouble']);
  });

  it('사용감은 고민이 아니다 — 결과에서 빠진다', () => {
    expect(effectiveConcerns(['trouble', 'dewy'], null)).toEqual(['trouble']);
  });

  it('사용감만 고른 회원은 고민이 빈 것으로 보고 피부타입에서 파생한다', () => {
    expect(effectiveConcerns(['dewy'], 'OILY')).toEqual(['sebum', 'pore']);
  });

  it('고민이 비면 SENSITIVE가 [soothe, gentle]로 파생된다', () => {
    expect(effectiveConcerns([], 'SENSITIVE')).toEqual(['soothe', 'gentle']);
  });

  it('고민도 피부타입도 없으면 빈 배열이다 — 티어0으로 내려간다', () => {
    expect(effectiveConcerns([], null)).toEqual([]);
  });

  it('알 수 없는 슬러그(구 어휘 잔재 등)는 조용히 버린다', () => {
    expect(effectiveConcerns(['PORE'], 'DRY')).toEqual(['moisture', 'barrier']);
  });

  it('고민 선택 순서를 그대로 지킨다 — 티어1의 우선순위가 이 순서다', () => {
    expect(effectiveConcerns(['bright', 'trouble'], null)).toEqual(['bright', 'trouble']);
  });
});

describe('preferredTextures — 사용감 tie-break용 슬러그', () => {
  it('concerns에 섞여 저장된 사용감만 골라낸다', () => {
    expect(preferredTextures(['trouble', 'dewy', 'fresh'])).toEqual(['dewy', 'fresh']);
  });

  it('사용감이 없으면 빈 배열이다', () => {
    expect(preferredTextures(['trouble'])).toEqual([]);
  });
});

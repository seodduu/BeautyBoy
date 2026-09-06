/**
 * 행동 점수 동등성 픽스처 생성기.
 *
 * 클라이언트의 `aggregate()`·`behaviorInput()` 실제 출력을 JSON으로 굳혀
 * Wave B-T1의 Python 동등 구현(build_table.py)이 맞춰야 할 기대값으로 넘긴다.
 * 기대값을 손으로 계산해 적으면 두 구현이 아니라 내 산수를 검증하게 된다.
 *
 * 실행: `cd frontend && npx vitest run --config bench/vitest.bench.config.ts bench/exportBehaviorCases.bench.ts`
 *
 * exportCases.bench.ts와 같은 방식이다. "테스트"가 아니라 산출물 생성기이고,
 * 프로덕션 TS 모듈을 그대로 불러오기 위해 vitest를 통로로 쓴다. 생성 과정에 가드 단언을 둔다.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { WEIGHT } from '../src/features/affinity/events';
import type { AffinityEvent } from '../src/features/affinity/events';
import { aggregate } from '../src/features/affinity/profile';
import { behaviorInput } from '../src/features/affinity/behaviorInput';

const OUT = resolve(__dirname, '../../tools/recsys/tests/fixtures/behavior-cases.json');

interface BehaviorCase {
  name: string;
  events: AffinityEvent[];
  aggregate: Record<string, number>;
  behaviorInput: number[];
}

function event(partial: Partial<AffinityEvent> = {}): AffinityEvent {
  return { goodsNo: 1, cat3: 'C001002', tags: ['moisture'], w: WEIGHT.view, ...partial };
}

function caseOf(name: string, events: AffinityEvent[]): BehaviorCase {
  return {
    name,
    events,
    aggregate: Object.fromEntries(aggregate(events)),
    behaviorInput: behaviorInput(events),
  };
}

describe('행동 점수 동등성 픽스처 생성', () => {
  it('aggregate 5건 + behaviorInput 5건을 내보낸다', () => {
    const cases: BehaviorCase[] = [
      // aggregate 쪽 5건. profile.test.ts의 케이스를 그대로 옮겼다.
      caseOf('aggregate-조회3회', [event(), event(), event()]),
      caseOf('aggregate-태그2개-찜', [event({ tags: ['moisture', 'soothe'], w: WEIGHT.wish })]),
      caseOf('aggregate-카테고리분리', [
        event({ cat3: 'C001002', tags: ['exfoliate'] }),
        event({ cat3: 'C002001', tags: ['exfoliate'] }),
      ]),
      caseOf('aggregate-태그없음', [event({ tags: [] })]),
      caseOf('aggregate-이벤트없음', []),

      // behaviorInput 쪽 5건. behaviorInput.test.ts의 케이스를 그대로 옮겼다.
      caseOf('behaviorInput-빈입력', []),
      caseOf('behaviorInput-카테고리합산', [
        event({ cat3: 'C002001', tags: ['moisture'], w: WEIGHT.view }),
        event({ cat3: 'C001001', tags: ['moisture'], w: WEIGHT.cart }),
      ]),
      caseOf('behaviorInput-최대값정규화', [
        event({ tags: ['sebum'], w: WEIGHT.cart }),
        event({ tags: ['sebum'], w: WEIGHT.cart }),
        event({ tags: ['moisture'], w: WEIGHT.cart }),
      ]),
      caseOf('behaviorInput-마지막칸', [event({ tags: ['gentle'] })]),
      caseOf('behaviorInput-모르는태그', [event({ tags: ['unknown'] })]),
    ];

    expect(cases).toHaveLength(10);
    expect(new Set(cases.map((c) => c.name)).size).toBe(10);
    expect(cases.every((c) => c.behaviorInput.length === 18)).toBe(true);

    // 가드: 최대 1이 나오는 케이스가 없으면 정규화 분기를 아무도 안 밟는다.
    expect(
      cases.some((c) => Math.max(...c.behaviorInput) === 1),
      '최대값이 1인 케이스가 없다. 정규화 분기를 검증하지 못한다',
    ).toBe(true);
    // 가드: 전부 0인 케이스가 없으면 max=0일 때 NaN을 막는 분기를 아무도 안 밟는다.
    expect(
      cases.some((c) => c.behaviorInput.every((value) => value === 0)),
      '전부 0인 케이스가 없다. NaN 방지 분기를 검증하지 못한다',
    ).toBe(true);

    const bundle = {
      generatedFrom:
        'frontend/bench/exportBehaviorCases.bench.ts가 만든다. 클라이언트 aggregate/behaviorInput의 실제 출력이므로 손으로 고치지 말고 생성기를 다시 돌린다.',
      cases,
    };
    mkdirSync(dirname(OUT), { recursive: true });
    writeFileSync(OUT, `${JSON.stringify(bundle, null, 1)}\n`, 'utf8');

    console.log(`[생성] 행동 점수 케이스 ${cases.length}건 → ${OUT}`);
  });
});

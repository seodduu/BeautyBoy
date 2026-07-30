/**
 * 골든 케이스 생성기 — 클라이언트 조합기의 실제 출력을 JSON으로 굳혀 백엔드 동등성 테스트의
 * 기대값으로 넘긴다. 그리고 k6가 회전 선택할 요청 본문 20개를 함께 내보낸다.
 *
 * 실행: `cd frontend && npx vitest run --config bench/vitest.bench.config.ts`
 *
 * 이 파일은 "테스트"가 아니라 산출물 생성기다. 다만 vitest로 돌리는 이유가 있다 —
 * 프로덕션 TS 모듈(확장자 없는 import, JSON import)을 그대로 불러오려면 번들러가 필요하고,
 * 새 번들 설정을 만드는 것보다 이미 있는 vitest를 통로로 쓰는 편이 단순하다.
 * 그래도 생성 과정에 **가드 단언**을 넣어 둔다: 케이스가 의도한 분기를 실제로 밟는지
 * (예: 26번이 접두사 합산 없이는 다른 답이 나오는지) 여기서 확인하지 않으면
 * "통과하지만 아무것도 안 보는 케이스"가 백엔드로 넘어간다.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { WEIGHT, toCat3 } from '../src/features/affinity/events';
import { aggregate } from '../src/features/affinity/profile';
import type { AffinityEvent } from '../src/features/affinity/events';
import type { ComposerSignals } from '../src/features/affinity/composer';
import {
  CONCERN_RULES,
  FLOW_RULES,
  MAX_EVENTS,
  POOLS,
  PROFILE_COUNT,
  type Conflict,
  behaviorEvents,
  composeChain,
  profileAt,
  toExpected,
  toServerRequest,
} from './harness';

const CASES_OUT = resolve(__dirname, '../../backend/src/test/resources/experiment/affinity-cases.json');
const PAYLOAD_OUT = resolve(__dirname, '../../tools/loadtest/affinity-payload.json');

interface GoldenCase {
  name: string;
  request: ReturnType<typeof toServerRequest>;
  expected: ReturnType<typeof toExpected>;
}

function caseOf(
  name: string,
  signals: ComposerSignals,
  events: AffinityEvent[],
  conflicts: Conflict[] | null = null,
): GoldenCase {
  return {
    name,
    request: toServerRequest(signals, events, conflicts),
    expected: toExpected(composeChain(signals, conflicts)),
  };
}

/** 게이트 없이 나온 인접 픽 쌍 — 조합기가 실제로 고른 조합만 막으므로 가장 빡빡한 픽스처다. */
function adjacentPairsOf(signals: ComposerSignals): Conflict[] {
  const chain = composeChain(signals);
  const pairs: Conflict[] = [];
  for (let s = 1; s < chain.length; s += 1) {
    const base = chain[s - 1].pick;
    const next = chain[s].pick;
    if (base !== null && next !== null) {
      pairs.push({ base: base.goodsNo, goodsNo: next.goodsNo });
    }
  }
  return pairs;
}

describe('골든 케이스 생성', () => {
  it('26 케이스와 k6 페이로드 20건을 내보낸다', () => {
    const cases: GoldenCase[] = [];

    // 1~20: 고민 0~3개 × 피부타입 4종 × 행동 3패턴. 정상 경로 전량.
    for (let i = 0; i < PROFILE_COUNT; i += 1) {
      const p = profileAt(i);
      cases.push(caseOf(`profile-${String(i).padStart(2, '0')}`, p.signals, p.events));
    }

    // 21: 신호 0 — 점수가 popularity뿐이라 인기순이 그대로 나와야 한다.
    const noSignal: ComposerSignals = { concerns: [], textures: [], affinity: new Map() };
    const noSignalCase = caseOf('no-signal', noSignal, []);
    expect(noSignalCase.expected[0].pick).toBe(POOLS[0].rows[0].goodsNo);
    cases.push(noSignalCase);

    // 22: 링버퍼 만석(50건) — 집계 규모 상한.
    const fullEvents = behaviorEvents(3, 'carts', MAX_EVENTS);
    expect(fullEvents).toHaveLength(50);
    cases.push(
      caseOf(
        'ring-buffer-full',
        { concerns: ['moisture'], textures: ['dewy'], affinity: aggregate(fullEvents) },
        fullEvents,
      ),
    );

    // 23: concernOverride — 히트 1개도 만점(2)으로 정규화하는 분기.
    const overrideEvents = behaviorEvents(5, 'views', 8);
    cases.push(
      caseOf(
        'concern-override',
        {
          concerns: ['soothe'],
          textures: [],
          affinity: aggregate(overrideEvents),
          concernOverride: true,
        },
        overrideEvents,
      ),
    );

    // 24: 궁합 게이트가 순위를 실제로 바꾸는 경로.
    const gatedProfile = profileAt(7);
    const pairs = adjacentPairsOf(gatedProfile.signals);
    expect(pairs.length).toBeGreaterThan(0);
    const gatedCase = caseOf('gated-conflicts', gatedProfile.signals, gatedProfile.events, pairs);
    // 게이트가 정말 무언가를 바꿨는지 확인한다 — 안 바뀌면 이 케이스는 24번이 아니라 7번의 복사다.
    const ungated = toExpected(composeChain(gatedProfile.signals));
    expect(gatedCase.expected).not.toEqual(ungated);
    cases.push(gatedCase);

    // 25: 2단계 후보 전량 CONFLICT → pick=null, 그 뒤 단계는 앵커 없이 계속된다.
    const base = ungated[0].pick;
    expect(base).not.toBeNull();
    const allGated: Conflict[] = POOLS[1].rows.map((row) => ({
      base: base as number,
      goodsNo: row.goodsNo,
    }));
    const allGatedCase = caseOf(
      'all-gated',
      gatedProfile.signals,
      gatedProfile.events,
      allGated,
    );
    expect(allGatedCase.expected[1].pick).toBeNull();
    expect(allGatedCase.expected[1].alternatives).toEqual([]);
    cases.push(allGatedCase);

    // 26: 접두사 합산 분기 — 클렌징 단계 코드는 4자(C002)인데 이벤트 키는 7자(C002001)다.
    // 직접 조회로 이식하면 친화도가 0이 되어 인기 1위가 그대로 나온다. 그 차이를 만드는 케이스를
    // 고정한다: 인기 1위가 아닌 클렌징 상품을 3회 담아 픽이 실제로 바뀌는 행을 찾는다.
    const leader = POOLS[0].rows[0].goodsNo;
    let prefixCase: GoldenCase | null = null;
    for (let r = 1; r < POOLS[0].rows.length && prefixCase === null; r += 1) {
      const row = POOLS[0].rows[r];
      const events: AffinityEvent[] = Array.from({ length: 3 }, () => ({
        goodsNo: row.goodsNo,
        cat3: toCat3(row.categoryCode),
        tags: row.tags,
        w: WEIGHT.cart,
      }));
      const signals: ComposerSignals = { concerns: [], textures: [], affinity: aggregate(events) };
      const built = caseOf('cleansing-prefix', signals, events);
      if (built.expected[0].pick !== leader) {
        prefixCase = built;
      }
    }
    // 못 찾으면 이 케이스는 아무것도 검증하지 못한다 — 조용히 넘기지 않고 실패시킨다.
    expect(prefixCase, '접두사 합산으로 픽이 바뀌는 클렌징 후보를 찾지 못했다').not.toBeNull();
    cases.push(prefixCase as GoldenCase);

    expect(cases).toHaveLength(26);
    expect(new Set(cases.map((c) => c.name)).size).toBe(26);

    const bundle = {
      generatedFrom:
        'frontend/bench/exportCases.bench.ts — 클라이언트 composeStep/aggregate의 실제 출력이다. 손으로 고치지 말고 생성기를 다시 돌린다.',
      flowRules: FLOW_RULES,
      concernRules: CONCERN_RULES,
      cases,
    };
    mkdirSync(dirname(CASES_OUT), { recursive: true });
    writeFileSync(CASES_OUT, `${JSON.stringify(bundle, null, 1)}\n`, 'utf8');

    // ── k6 페이로드: 프로필 5종 × 이벤트 5·8·20·50건 = 20건. 실사용 분포를 흉내 낸다.
    const payloads = [];
    for (const eventCount of [5, 8, 20, 50]) {
      // 행동 패턴이 'none'인 인덱스(index % 3 === 0)는 이벤트가 0건이라 제외한다 —
      // 서버 부하를 재는 페이로드에 이벤트 없는 요청을 섞으면 집계 비용이 빠진다.
      for (const index of [1, 2, 4, 5, 7]) {
        const p = profileAt(index, eventCount);
        payloads.push({
          name: `profile-${index}-events-${eventCount}`,
          body: toServerRequest(p.signals, p.events),
        });
      }
    }
    expect(payloads).toHaveLength(20);
    expect(payloads.every((p) => p.body.events.length > 0)).toBe(true);
    writeFileSync(PAYLOAD_OUT, `${JSON.stringify(payloads, null, 1)}\n`, 'utf8');

    console.log(
      `[생성] 골든 케이스 ${cases.length}건 → ${CASES_OUT}\n` +
        `[생성] k6 페이로드 ${payloads.length}건 → ${PAYLOAD_OUT}`,
    );
  });
});

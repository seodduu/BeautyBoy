/**
 * 클라이언트 계산 시간 계측 — "기기에 위임한 개인화 계산 한 번"이 몇 ms인가.
 *
 * 재는 단위는 **화면 하나의 조합 전량**이다: `aggregate(events)` + `composeStep()` × 5단계.
 * 사용자가 메인 화면에서 기다리는 계산이 정확히 그것이고, 서버 참조 구현도 같은 경계로 잘려 있다
 * (요청 하나가 5단계 체인 전체를 계산해 돌려준다).
 *
 * **재지 않는 것**: localStorage 읽기, 풀 조회(`/goods`), 규칙 조회(`/routine/flow-rules`),
 * 궁합 조회. 양쪽 다 같은 칼로 자른다(goal 판단 기준 1항 — 계산과 IO를 분리한다).
 *
 * 계측 코드는 프로덕션 경로에 없다 — 이 파일은 `src/`를 import만 하고 수정하지 않는다.
 *
 * 실행: `cd frontend && npx vitest run --config bench/vitest.bench.config.ts`
 */
import { cpus, totalmem, release, arch } from 'node:os';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { aggregate } from '../src/features/affinity/profile';
import { MAX_EVENTS, behaviorEvents, composeChain, profileAt } from './harness';

const OUT = resolve(__dirname, '../../docs/loadtest/2026-07-30-affinity-benchmark/client-bench.json');

/** 본 측정 반복 수 — goal 3항이 지정한 N. */
const N = 1_000;
/** 워밍업. V8이 이 경로를 최적화하기 전 몇십 회는 인터프리터 실행이라 p50을 통째로 왜곡한다. */
const WARMUP = 200;

/**
 * k6와 같은 방식(선형 보간)으로 백분위를 낸다. 두 측정의 백분위 정의가 다르면 표를 나란히 놓을 수
 * 없다 — k6 Trend는 `index = p/100 * (n-1)` 위치를 이웃 두 값 사이에서 보간한다.
 */
function percentile(sorted: number[], p: number): number {
  const index = (p / 100) * (sorted.length - 1);
  const lower = Math.floor(index);
  const upper = Math.ceil(index);
  if (lower === upper) {
    return sorted[lower];
  }
  return sorted[lower] + (index - lower) * (sorted[upper] - sorted[lower]);
}

function measure(label: string, eventCount: number, run: () => unknown) {
  for (let i = 0; i < WARMUP; i += 1) {
    run();
  }
  const samples: number[] = new Array(N);
  for (let i = 0; i < N; i += 1) {
    const start = performance.now();
    run();
    samples[i] = performance.now() - start;
  }
  const sorted = [...samples].sort((a, b) => a - b);
  const avg = samples.reduce((sum, x) => sum + x, 0) / samples.length;
  return {
    label,
    eventCount,
    iterations: N,
    warmup: WARMUP,
    unit: 'ms',
    avg,
    min: sorted[0],
    p50: percentile(sorted, 50),
    p90: percentile(sorted, 90),
    p95: percentile(sorted, 95),
    p99: percentile(sorted, 99),
    max: sorted[sorted.length - 1],
  };
}

describe('클라이언트 계산 시간', () => {
  it(`한 화면 조합(aggregate + composeStep×5)을 ${N}회 재고 p50/p95를 낸다`, () => {
    // 전형: 프로필 인덱스 1(고민 moisture + views 8건). simulation.test.ts의 기본 이벤트 수와 같다.
    const typical = profileAt(1, 8);
    expect(typical.events).toHaveLength(8);
    // 상한: 링버퍼 만석 50건. 집계 규모가 최대일 때의 값이라 "최악에도 이만큼"의 근거가 된다.
    const worstEvents = behaviorEvents(1, 'carts', MAX_EVENTS);
    expect(worstEvents).toHaveLength(50);

    const results = [
      measure('typical-8-events', 8, () =>
        composeChain({ ...typical.signals, affinity: aggregate(typical.events) }),
      ),
      measure('ring-buffer-full-50-events', 50, () =>
        composeChain({ ...typical.signals, affinity: aggregate(worstEvents) }),
      ),
    ];

    // 계산이 실제로 일어났는지 확인한다 — 죽은 코드를 재고 0ms를 보고하면 안 된다.
    expect(composeChain({ ...typical.signals, affinity: aggregate(typical.events) })[0].pick)
      .not.toBeNull();
    expect(results.every((r) => r.p50 > 0)).toBe(true);

    const cpu = cpus();
    const out = {
      measuredWith: 'frontend/bench/clientBench.bench.ts (performance.now(), vitest node 환경)',
      scope:
        'aggregate(events) + composeStep() × 5단계 = 화면 하나의 조합 전량. localStorage 읽기·풀 조회·규칙 조회·궁합 조회는 제외(서버 참조 구현과 같은 경계).',
      percentileMethod: 'k6 Trend와 동일한 선형 보간 (index = p/100 * (n-1))',
      environment: {
        chip: cpu[0]?.model ?? 'unknown',
        cores: cpu.length,
        memoryBytes: totalmem(),
        arch: arch(),
        os: `darwin ${release()}`,
        node: process.version,
        note: 'Node와 Chrome은 같은 V8이고 대상 코드는 외부 의존이 없는 순수 계산이라 이 수치를 브라우저 대리값으로 쓴다(리포트 한계 절 참고).',
      },
      results,
    };
    mkdirSync(dirname(OUT), { recursive: true });
    writeFileSync(OUT, `${JSON.stringify(out, null, 2)}\n`, 'utf8');

    for (const r of results) {
      console.log(
        `[클라] ${r.label} — p50 ${r.p50.toFixed(4)}ms / p95 ${r.p95.toFixed(4)}ms / ` +
          `p99 ${r.p99.toFixed(4)}ms / avg ${r.avg.toFixed(4)}ms (N=${N})`,
      );
    }
    console.log(`[클라] 원본 → ${OUT}`);
  });
});

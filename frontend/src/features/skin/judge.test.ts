import { describe, expect, it } from 'vitest';
import { judge, suggestConcern, toRecord } from './judge';
import { GATE, type Measurement, type RoiStats, type SkinRecord, type Verdict } from './types';

const T = GATE.REDNESS_NO_CHANGE;
const D = GATE.MAX_FOREHEAD_L_DELTA_FOR_COMPARE;

function okStats(meanA: number): RoiStats {
  return { meanL: 65, meanA, meanB: 15, stdL: 5, excludedRatio: 0, pixels: 1000 };
}

function rec(redness: number, foreheadL: number): SkinRecord {
  return { v: 1, at: '2026-11-05T00:00:00.000Z', redness, foreheadL, foreheadA: 15, cheekA: 16, faceWidthRatio: 0.5 };
}

function meas(redness: number, foreheadL: number): Measurement {
  return {
    redness,
    foreheadL,
    foreheadA: 0,
    foreheadExcludedRatio: 0,
    cheekA: 0,
    faceWidthRatio: 0,
    noseCenterRatio: 0,
    clippedRatio: 0,
    rois: { forehead: okStats(15), cheekLeft: okStats(15), cheekRight: okStats(15), nose: okStats(15) },
  };
}

describe('toRecord', () => {
  it('toRecord는_다섯_수치와_시각을_담는다', () => {
    const m = { ...meas(1.3, 60), foreheadA: 15, cheekA: 16.3, faceWidthRatio: 0.5 };
    const r = toRecord(m, new Date('2026-11-05T01:02:03.000Z'));
    expect(r.at).toBe('2026-11-05T01:02:03.000Z');
    expect(r.v).toBe(1);
    expect(r.redness).toBe(1.3);
    expect(r.foreheadL).toBe(60);
    expect(r.foreheadA).toBe(15);
    expect(r.cheekA).toBe(16.3);
    expect(r.faceWidthRatio).toBe(0.5);
    expect(Object.keys(r)).toHaveLength(7);
  });
});

describe('judge', () => {
  it('이력없으면_first', () => {
    expect(judge(meas(1, 60), [])).toEqual({ kind: 'first' });
  });

  it('최근_3건이_모두_조명불일치면_lighting_mismatch', () => {
    const history = [rec(1, 80), rec(1, 80), rec(1, 80), rec(1, 60)];
    const v = judge(meas(1, 60), history);
    expect(v.kind).toBe('lighting-mismatch');
    if (v.kind === 'lighting-mismatch') expect(v.deltaL).toBe(-20);
  });

  it('조명차이_정확히_D면_비교한다', () => {
    expect(judge(meas(1, 60 + D), [rec(1, 60)]).kind).toBe('change');
  });

  it('기준값은_비교가능한_최근_3건의_중앙값', () => {
    const history = [rec(1.0, 60), rec(5.0, 60), rec(1.2, 60), rec(9, 60)];
    const v = judge(meas(1.1, 60), history);
    expect(v.kind).toBe('change');
    if (v.kind === 'change') {
      expect(v.baseline).toBe(1.2);
      expect(v.label).toBe('변화 없음');
    }
  });

  it('조명불일치_건은_중앙값에서_뺀다', () => {
    const history = [rec(1.0, 60), rec(5.0, 90), rec(1.2, 60)];
    const v = judge(meas(1.1, 60), history);
    expect(v.kind).toBe('change');
    if (v.kind === 'change') expect(v.baseline).toBeCloseTo(1.1, 9);
  });

  it('변화가_T_미만이면_변화없음', () => {
    const v = judge(meas(T - 0.01, 60), [rec(0, 60)]);
    expect(v.kind === 'change' && v.label).toBe('변화 없음');
  });

  it('변화가_정확히_T면_증가', () => {
    const v = judge(meas(T, 60), [rec(0, 60)]);
    expect(v.kind).toBe('change');
    if (v.kind === 'change') {
      expect(v.label).toBe('증가');
      expect(Math.abs(v.delta - T)).toBeLessThan(1e-9);
    }
  });

  it('변화가_음수_T면_감소', () => {
    const v = judge(meas(-T, 60), [rec(0, 60)]);
    expect(v.kind === 'change' && v.label).toBe('감소');
  });
});

describe('suggestConcern', () => {
  const increase: Verdict = { kind: 'change', delta: 1, baseline: 0, label: '증가' };

  it('증가이고_soothe없으면_제안', () => {
    expect(suggestConcern(increase, { skinType: 'OILY', concerns: ['sebum'] })).toBe('soothe');
  });

  it('증가여도_soothe있으면_제안없음', () => {
    expect(suggestConcern(increase, { skinType: 'OILY', concerns: ['soothe'] })).toBeNull();
  });

  it('증가여도_SENSITIVE면_제안없음', () => {
    expect(suggestConcern(increase, { skinType: 'SENSITIVE', concerns: [] })).toBeNull();
  });

  it('감소_변화없음_first_lighting은_제안없음', () => {
    const profile = { skinType: 'OILY', concerns: [] as string[] };
    const verdicts: Verdict[] = [
      { kind: 'change', delta: -1, baseline: 0, label: '감소' },
      { kind: 'change', delta: 0, baseline: 0, label: '변화 없음' },
      { kind: 'first' },
      { kind: 'lighting-mismatch', deltaL: 20 },
    ];
    for (const v of verdicts) expect(suggestConcern(v, profile)).toBeNull();
  });
});

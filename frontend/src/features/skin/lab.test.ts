import { describe, expect, it } from 'vitest';
import { roiStats, srgbToLab } from './lab';
import { GATE } from './types';

/** 같은 색 count 개를 RGBA 배열로 만든다. */
function px(r: number, g: number, b: number, count: number, alpha = 255): number[] {
  const out: number[] = [];
  for (let i = 0; i < count; i++) out.push(r, g, b, alpha);
  return out;
}
const arr = (...parts: number[][]) => new Uint8ClampedArray(parts.flat());

describe('srgbToLab', () => {
  it('흰색은_L100_a0_b0', () => {
    const [L, a, b] = srgbToLab(255, 255, 255);
    expect(Math.abs(L - 100)).toBeLessThan(0.05);
    expect(Math.abs(a)).toBeLessThan(0.02);
    expect(Math.abs(b)).toBeLessThan(0.02);
  });

  it('검정은_0_0_0', () => {
    const [L, a, b] = srgbToLab(0, 0, 0);
    expect(L).toBeCloseTo(0, 2);
    expect(a).toBeCloseTo(0, 2);
    expect(b).toBeCloseTo(0, 2);
  });

  it('순빨강은_53_24_80_09_67_20', () => {
    const [L, a, b] = srgbToLab(255, 0, 0);
    expect(Math.abs(L - 53.24)).toBeLessThanOrEqual(0.05);
    expect(Math.abs(a - 80.09)).toBeLessThanOrEqual(0.05);
    expect(Math.abs(b - 67.2)).toBeLessThanOrEqual(0.05);
  });

  it('순파랑은_b가_음수', () => {
    const [, , b] = srgbToLab(0, 0, 255);
    expect(b).toBeLessThan(-100);
  });
});

describe('roiStats', () => {
  it('roiStats는_RGBA_평균과_L표준편차를_낸다', () => {
    const s = roiStats(arr([255, 0, 0, 255, 0, 0, 255, 255]));
    const red = srgbToLab(255, 0, 0);
    const blue = srgbToLab(0, 0, 255);
    expect(s.pixels).toBe(2);
    expect(Math.abs(s.meanA - (red[1] + blue[1]) / 2)).toBeLessThanOrEqual(0.01);
    expect(Math.abs(s.stdL - Math.abs(red[0] - blue[0]) / 2)).toBeLessThanOrEqual(0.01);
    expect(s.excludedRatio).toBe(0);
  });

  it('roiStats는_어두운_이상치를_평균에서_뺀다', () => {
    const s = roiStats(arr(px(200, 150, 140, 95), px(30, 30, 30, 5)));
    const skin = srgbToLab(200, 150, 140);
    expect(Math.abs(s.meanL - skin[0])).toBeLessThanOrEqual(0.01);
    expect(Math.abs(s.excludedRatio - 0.05)).toBeLessThan(1e-9);
    expect(s.stdL).toBeGreaterThan(5);
  });

  it('roiStats_작은_음영은_절사하지_않는다', () => {
    const s = roiStats(arr(px(200, 150, 140, 90), px(190, 140, 130, 10)));
    const a = srgbToLab(200, 150, 140);
    const b = srgbToLab(190, 140, 130);
    expect(s.excludedRatio).toBe(0);
    expect(Math.abs(s.meanL - (a[0] * 0.9 + b[0] * 0.1))).toBeLessThanOrEqual(0.01);
  });

  it('roiStats_머리카락_20퍼센트는_절사비율_0_2', () => {
    const s = roiStats(arr(px(200, 150, 125, 1600), px(75, 55, 52, 400)));
    const skin = srgbToLab(200, 150, 125);
    expect(Math.abs(s.excludedRatio - 0.2)).toBeLessThan(1e-9);
    expect(Math.abs(s.meanA - skin[1])).toBeLessThanOrEqual(0.01);
  });

  it('roiStats_머리카락_20퍼센트는_절사되고_keptStdL은_0', () => {
    const s = roiStats(arr(px(200, 150, 125, 1600), px(75, 55, 52, 400)));
    expect(Math.abs(s.excludedRatio - 0.2)).toBeLessThan(1e-9);
    expect(s.keptStdL).toBeLessThan(1e-6);
  });

  it('roiStats_머리카락_50퍼센트는_절사가_꺼지고_keptStdL이_크다', () => {
    // 가림이 절반이면 중앙값이 머리카락 쪽으로 넘어가 절사가 꺼진다. 남은 분포의 분산으로 잡는다(설계 4.3·4.5 정정).
    const s = roiStats(arr(px(200, 150, 125, 1000), px(75, 55, 52, 1000)));
    expect(s.excludedRatio).toBe(0);
    expect(s.keptStdL).toBeGreaterThan(GATE.MAX_FOREHEAD_KEPT_STD_L);
  });

  it('roiStats_머리카락_45퍼센트도_절사된다', () => {
    const s = roiStats(arr(px(200, 150, 125, 1100), px(75, 55, 52, 900)));
    expect(Math.abs(s.excludedRatio - 0.45)).toBeLessThan(1e-9);
  });

  it('roiStats는_알파를_무시한다', () => {
    const opaque = roiStats(arr(px(200, 150, 140, 4, 255)));
    const clear = roiStats(arr(px(200, 150, 140, 4, 0)));
    expect(clear).toEqual(opaque);
  });

  it('roiStats_단색은_stdL_0_절사없음', () => {
    const s = roiStats(arr(px(200, 150, 140, 4)));
    expect(Math.abs(s.stdL)).toBeLessThan(1e-9);
    expect(Math.abs(s.keptStdL)).toBeLessThan(1e-9);
    expect(s.excludedRatio).toBe(0);
    expect(s.meanL).toBeCloseTo(srgbToLab(200, 150, 140)[0], 6);
  });
});

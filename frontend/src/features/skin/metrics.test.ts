import { describe, expect, it } from 'vitest';
import { gateCapture, measure } from './metrics';
import { GATE, type FaceGeometry, type Measurement, type RoiKey, type RoiStats } from './types';

function okStats(meanA: number): RoiStats {
  return { meanL: 65, meanA, meanB: 15, stdL: 5, keptStdL: 2, excludedRatio: 0, pixels: 1000 };
}

const BASE_GEO: FaceGeometry = { faceWidthRatio: 0.5, noseCenterRatio: 0.5, clippedRatio: 0 };

/** 게이트를 통과하는 기본 측정에서 한 값만 바꾼다. */
function measurementWith(
  geo: Partial<FaceGeometry> = {},
  forehead: Partial<RoiStats> = {},
): Measurement {
  const rois: Record<RoiKey, RoiStats> = {
    forehead: { ...okStats(15), ...forehead },
    cheekLeft: okStats(16),
    cheekRight: okStats(16),
    nose: okStats(15),
  };
  return measure(rois, { ...BASE_GEO, ...geo });
}

describe('measure', () => {
  it('redness는_볼평균a_빼기_이마a다', () => {
    const m = measure(
      {
        forehead: okStats(15.43),
        cheekLeft: okStats(16.78),
        cheekRight: okStats(17.49),
        nose: okStats(15),
      },
      BASE_GEO,
    );
    expect(Math.abs(m.redness - 1.705)).toBeLessThan(1e-6);
  });
});

describe('gateCapture', () => {
  it('gate_얼굴없음', () => {
    expect(gateCapture(0, null)).toBe('no-face');
  });

  it('gate_프레임밖', () => {
    expect(gateCapture(1, measurementWith({ clippedRatio: GATE.MAX_CLIPPED_RATIO + 0.01 }))).toBe('face-out-of-frame');
  });

  it('gate_얼굴작음', () => {
    expect(gateCapture(1, measurementWith({ faceWidthRatio: GATE.MIN_FACE_WIDTH_RATIO - 0.01 }))).toBe('face-too-small');
    expect(gateCapture(1, measurementWith({ faceWidthRatio: GATE.MIN_FACE_WIDTH_RATIO }))).toBeNull();
  });

  it('gate_정면아님', () => {
    expect(gateCapture(1, measurementWith({ noseCenterRatio: 0.5 + GATE.MAX_NOSE_OFFSET + 0.01 }))).toBe('face-not-frontal');
  });

  it('gate_어두움', () => {
    expect(gateCapture(1, measurementWith({}, { meanL: GATE.MIN_FOREHEAD_L - 0.1 }))).toBe('too-dark');
    expect(gateCapture(1, measurementWith({}, { meanL: GATE.MIN_FOREHEAD_L }))).toBeNull();
  });

  it('gate_밝음', () => {
    expect(gateCapture(1, measurementWith({}, { meanL: GATE.MAX_FOREHEAD_L + 0.1 }))).toBe('too-bright');
  });

  it('gate_앞머리', () => {
    expect(gateCapture(1, measurementWith({}, { excludedRatio: GATE.MAX_FOREHEAD_EXCLUDED + 0.01 }))).toBe('forehead-occluded');
    expect(gateCapture(1, measurementWith({}, { excludedRatio: GATE.MAX_FOREHEAD_EXCLUDED }))).toBeNull();
  });

  it('gate_절사후_이마분산이_크면_forehead_occluded', () => {
    expect(gateCapture(1, measurementWith({}, { keptStdL: GATE.MAX_FOREHEAD_KEPT_STD_L + 0.01 }))).toBe('forehead-occluded');
    expect(gateCapture(1, measurementWith({}, { keptStdL: GATE.MAX_FOREHEAD_KEPT_STD_L }))).toBeNull();
  });

  it('gate_비유한수는_analyze_failed', () => {
    // NaN 은 모든 비교가 거짓이라 검사 없이는 게이트를 통과한다.
    expect(gateCapture(1, measurementWith({}, { meanL: Number.NaN }))).toBe('analyze-failed');
    expect(gateCapture(1, measurementWith({ faceWidthRatio: Number.POSITIVE_INFINITY }))).toBe('analyze-failed');
  });

  it('gate_우선순위는_프레임밖_크기_정면_과다노출_앞머리_어두움_순', () => {
    expect(gateCapture(1, measurementWith({ clippedRatio: 0.5, faceWidthRatio: 0.1 }, { meanL: 10 }))).toBe('face-out-of-frame');
    expect(gateCapture(1, measurementWith({ clippedRatio: 0, faceWidthRatio: 0.1 }, { meanL: 10 }))).toBe('face-too-small');
    expect(gateCapture(1, measurementWith({}, { meanL: GATE.MAX_FOREHEAD_L + 1, keptStdL: 20 }))).toBe('too-bright');
  });

  it('gate_앞머리가_절반넘게_덮어_이마평균이_어두워지면_too_dark_가_아니라_forehead_occluded', () => {
    // 머리카락 L* 20 과 피부 L* 65 가 섞이면 평균이 35 아래로 내려가고 분포는 두 봉우리라 편차가 크다.
    expect(gateCapture(1, measurementWith({}, { meanL: GATE.MIN_FOREHEAD_L - 2, keptStdL: 20 }))).toBe('forehead-occluded');
    expect(gateCapture(1, measurementWith({}, { meanL: GATE.MIN_FOREHEAD_L - 2, excludedRatio: 0.3 }))).toBe('forehead-occluded');
    // 가림 없이 고르게 어두우면 그대로 too-dark.
    expect(gateCapture(1, measurementWith({}, { meanL: GATE.MIN_FOREHEAD_L - 2, keptStdL: 2 }))).toBe('too-dark');
  });
});

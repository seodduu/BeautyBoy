import { describe, expect, it } from 'vitest';
import { gateCapture, measure } from './metrics';
import { GATE, type FaceGeometry, type Measurement, type RoiKey, type RoiStats } from './types';

function okStats(meanA: number): RoiStats {
  return { meanL: 65, meanA, meanB: 15, stdL: 5, excludedRatio: 0, pixels: 1000 };
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

  it('gate_우선순위는_프레임밖_크기_정면_밝기_앞머리_순', () => {
    expect(gateCapture(1, measurementWith({ clippedRatio: 0.5, faceWidthRatio: 0.1 }, { meanL: 10 }))).toBe('face-out-of-frame');
    expect(gateCapture(1, measurementWith({ clippedRatio: 0, faceWidthRatio: 0.1 }, { meanL: 10 }))).toBe('face-too-small');
  });
});

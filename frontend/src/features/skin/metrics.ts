import { GATE, type FaceGeometry, type GateError, type Measurement, type RoiKey, type RoiStats } from './types';

export function measure(rois: Record<RoiKey, RoiStats>, geo: FaceGeometry): Measurement {
  const cheekA = (rois.cheekLeft.meanA + rois.cheekRight.meanA) / 2;
  return {
    redness: cheekA - rois.forehead.meanA,
    foreheadL: rois.forehead.meanL,
    foreheadA: rois.forehead.meanA,
    foreheadExcludedRatio: rois.forehead.excludedRatio,
    cheekA,
    ...geo,
    rois,
  };
}

/** 검사 순서가 곧 우선순위다. */
export function gateCapture(faces: number, m: Measurement | null): GateError | null {
  if (faces === 0 || m === null) return 'no-face';
  // NaN 은 아래 비교가 전부 거짓이라 그대로 통과한다. 계산이 깨진 측정은 분석 실패로 거부한다.
  const numbers = [m.redness, m.foreheadL, m.foreheadA, m.cheekA, m.foreheadExcludedRatio, m.faceWidthRatio,
    m.noseCenterRatio, m.clippedRatio, m.rois.forehead.keptStdL];
  if (!numbers.every(Number.isFinite)) return 'analyze-failed';
  if (m.clippedRatio > GATE.MAX_CLIPPED_RATIO) return 'face-out-of-frame';
  if (m.faceWidthRatio < GATE.MIN_FACE_WIDTH_RATIO) return 'face-too-small';
  if (Math.abs(m.noseCenterRatio - 0.5) > GATE.MAX_NOSE_OFFSET) return 'face-not-frontal';
  if (m.foreheadL < GATE.MIN_FOREHEAD_L) return 'too-dark';
  if (m.foreheadL > GATE.MAX_FOREHEAD_L) return 'too-bright';
  if (m.foreheadExcludedRatio > GATE.MAX_FOREHEAD_EXCLUDED) return 'forehead-occluded';
  if (m.rois.forehead.keptStdL > GATE.MAX_FOREHEAD_KEPT_STD_L) return 'forehead-occluded';
  return null;
}

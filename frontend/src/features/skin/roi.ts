import { LM_CHEEK_EDGE_LEFT, LM_CHEEK_EDGE_RIGHT, LM_NOSE_TIP, ROI_SHRINK, type Box } from './types';

export function roiBox(
  landmarks: ReadonlyArray<{ x: number; y: number }>,  // 정규화 좌표(0~1)
  indices: readonly number[],
  width: number,
  height: number,
): Box {
  const xs = indices.map((i) => landmarks[i].x * width);
  const ys = indices.map((i) => landmarks[i].y * height);
  const x0 = Math.min(...xs), x1 = Math.max(...xs);
  const y0 = Math.min(...ys), y1 = Math.max(...ys);
  const dx = (x1 - x0) * ROI_SHRINK, dy = (y1 - y0) * ROI_SHRINK;
  return {
    x: Math.round(x0 + dx),
    y: Math.round(y0 + dy),
    w: Math.max(1, Math.round(x1 - x0 - 2 * dx)),
    h: Math.max(1, Math.round(y1 - y0 - 2 * dy)),
  };
}

/** 박스를 프레임 안으로 자른다. 잘린 면적 비율도 돌려준다(게이트 'face-out-of-frame' 용). */
export function clampBox(box: Box, width: number, height: number): { box: Box; clippedRatio: number } {
  const x0 = Math.max(0, box.x), y0 = Math.max(0, box.y);
  const x1 = Math.min(width, box.x + box.w), y1 = Math.min(height, box.y + box.h);
  const w = Math.max(0, x1 - x0), h = Math.max(0, y1 - y0);
  const clippedRatio = 1 - (w * h) / (box.w * box.h);
  return { box: { x: x0, y: y0, w: Math.max(1, w), h: Math.max(1, h) }, clippedRatio };
}

/** 얼굴 폭 비율 = 전체 랜드마크(468+10)의 정규화 x 최대값 - 최소값. 프레임 폭과 무관하다. */
export function faceWidthRatio(landmarks: ReadonlyArray<{ x: number }>): number {
  let min = Infinity, max = -Infinity;
  for (const p of landmarks) { if (p.x < min) min = p.x; if (p.x > max) max = p.x; }
  return max - min;
}

/**
 * 좌우 회전(yaw) 대용값. 코끝(1)이 좌우 광대(234, 454) 사이 어디에 있는지 0~1 로 돌려준다.
 * 정면이면 0.5 부근. 고개를 돌리면 한쪽으로 치우친다.
 */
export function noseCenterRatio(landmarks: ReadonlyArray<{ x: number }>): number {
  const l = landmarks[LM_CHEEK_EDGE_LEFT].x, r = landmarks[LM_CHEEK_EDGE_RIGHT].x;
  return (landmarks[LM_NOSE_TIP].x - l) / (r - l);
}

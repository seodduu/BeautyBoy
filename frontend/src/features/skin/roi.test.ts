import { describe, expect, it } from 'vitest';
import { clampBox, faceWidthRatio, noseCenterRatio, roiBox } from './roi';
import { ROI_LANDMARKS, ROI_ORDER } from './types';

/** 478개 랜드마크 배열. 기본 x 는 0.5, 필요한 인덱스만 덮어쓴다. */
function landmarks478(overrides: Record<number, number>): { x: number; y: number }[] {
  return Array.from({ length: 478 }, (_, i) => ({ x: overrides[i] ?? 0.5, y: 0.5 }));
}

describe('roiBox', () => {
  it('roiBox는_바운딩박스를_각변_15퍼센트_줄인다', () => {
    const lm = [{ x: 0.2, y: 0.2 }, { x: 0.6, y: 0.6 }];
    expect(roiBox(lm, [0, 1], 1000, 1000)).toEqual({ x: 260, y: 260, w: 280, h: 280 });
  });

  it('roiBox는_최소_1픽셀을_보장한다', () => {
    const lm = [{ x: 0.4, y: 0.4 }, { x: 0.4, y: 0.4 }];
    const box = roiBox(lm, [0, 1], 1000, 1000);
    expect(box.w).toBe(1);
    expect(box.h).toBe(1);
  });
});

describe('clampBox', () => {
  it('clampBox는_프레임_안으로_자르고_잘린_비율을_낸다', () => {
    const r = clampBox({ x: -50, y: 0, w: 100, h: 100 }, 1000, 1000);
    expect(r.box).toEqual({ x: 0, y: 0, w: 50, h: 100 });
    expect(Math.abs(r.clippedRatio - 0.5)).toBeLessThan(1e-9);
  });

  it('clampBox_안에_있으면_0', () => {
    const r = clampBox({ x: 10, y: 10, w: 20, h: 20 }, 1000, 1000);
    expect(r.box).toEqual({ x: 10, y: 10, w: 20, h: 20 });
    expect(r.clippedRatio).toBe(0);
  });
});

describe('faceWidthRatio', () => {
  it('faceWidthRatio는_전체_정규화_x_범위다', () => {
    const lm = [{ x: 0.3 }, { x: 0.5 }, { x: 0.7 }, { x: 0.4 }, { x: 0.6 }];
    expect(Math.abs(faceWidthRatio(lm) - 0.4)).toBeLessThan(1e-9);
  });
});

describe('noseCenterRatio', () => {
  it('noseCenterRatio는_코끝의_좌우_위치다', () => {
    expect(Math.abs(noseCenterRatio(landmarks478({ 234: 0.2, 454: 0.8, 1: 0.5 })) - 0.5)).toBeLessThan(1e-9);
    expect(Math.abs(noseCenterRatio(landmarks478({ 234: 0.2, 454: 0.8, 1: 0.26 })) - 0.1)).toBeLessThan(1e-9);
  });
});

describe('ROI_LANDMARKS', () => {
  it('ROI_LANDMARKS_네_부위_인덱스가_설계값과_같다', () => {
    expect(ROI_LANDMARKS.forehead[0]).toBe(67);
    expect(ROI_LANDMARKS.cheekLeft[0]).toBe(117);
    expect(ROI_LANDMARKS.cheekRight[0]).toBe(346);
    expect(ROI_LANDMARKS.nose[0]).toBe(6);
    expect(ROI_LANDMARKS.forehead).toHaveLength(10);
    expect(ROI_LANDMARKS.cheekLeft).toHaveLength(8);
    expect(ROI_LANDMARKS.cheekRight).toHaveLength(8);
    expect(ROI_LANDMARKS.nose).toHaveLength(8);
    expect(ROI_ORDER).toEqual(['forehead', 'cheekLeft', 'cheekRight', 'nose']);
  });
});

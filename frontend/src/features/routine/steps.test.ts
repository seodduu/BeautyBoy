import { describe, expect, it } from 'vitest';
import { ROUTINE_SECTION_SIZE, ROUTINE_STEPS } from './steps';

describe('ROUTINE_STEPS — 루틴 매핑 상수', () => {
  it('구성 사양 2장의 5단계를 순서대로 담는다', () => {
    expect(ROUTINE_STEPS.map((step) => step.label)).toEqual([
      '클렌징',
      '토너/스킨',
      '에센스/세럼',
      '로션/크림',
      '선크림',
    ]);
  });

  it('categoryCode가 실 시드(V12) 코드와 정확히 일치한다', () => {
    expect(ROUTINE_STEPS.map((step) => step.categoryCode)).toEqual([
      'C002',
      'C001001',
      'C001002',
      'C001003',
      'C004001',
    ]);
  });

  it('order는 1부터 5까지 빈틈없이 증가한다', () => {
    expect(ROUTINE_STEPS.map((step) => step.order)).toEqual([1, 2, 3, 4, 5]);
  });

  it('id는 앵커로 쓰이므로 서로 겹치지 않는다', () => {
    const ids = ROUTINE_STEPS.map((step) => step.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('모든 단계가 카피와 레포 내부 이미지 경로를 갖는다', () => {
    for (const step of ROUTINE_STEPS) {
      expect(step.copy.length, `${step.label}의 카피`).toBeGreaterThan(0);
      // 외부 URL 직접 참조 금지 — public/ 아래 절대경로여야 한다.
      expect(step.image, `${step.label}의 이미지`).toMatch(/^\/images\/routine\//);
    }
  });

  it('섹션당 상품 개수는 4개다(한 줄 + 더보기)', () => {
    expect(ROUTINE_SECTION_SIZE).toBe(4);
  });
});

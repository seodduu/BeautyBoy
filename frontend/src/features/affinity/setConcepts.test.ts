import { describe, expect, it } from 'vitest';
import type { SkinType } from '../../api/auth';
import { CONCERNS } from '../../components/skin-profile/SkinProfileFields';
import { FALLBACK_CONCEPTS, SET_COUNT, deriveSetConcepts } from './setConcepts';

describe('deriveSetConcepts — 파생 사다리(스펙 §4)', () => {
  it('고민 3개 이상이면 선택 순서대로 앞 3개, 전부 개인화', () => {
    const result = deriveSetConcepts(['bright', 'soothe', 'exfoliate', 'pore'], 'OILY');
    expect(result.map((c) => c.slug)).toEqual(['bright', 'soothe', 'exfoliate']);
    expect(result.every((c) => c.personalized)).toBe(true);
  });

  it('고민 1개(pore) + OILY — 파생 sebum 보충, 파생 pore는 중복 소거, 3번째는 폴백 trouble', () => {
    const result = deriveSetConcepts(['pore'], 'OILY');
    expect(result.map((c) => c.slug)).toEqual(['pore', 'sebum', 'trouble']);
    expect(result.map((c) => c.personalized)).toEqual([true, true, false]);
  });

  it('고민 0개 + DRY — 피부타입 파생(moisture·barrier) 뒤 폴백 pore (폴백 moisture는 중복 소거)', () => {
    const result = deriveSetConcepts([], 'DRY');
    expect(result.map((c) => c.slug)).toEqual(['moisture', 'barrier', 'pore']);
    expect(result.map((c) => c.personalized)).toEqual([true, true, false]);
  });

  it('비로그인([], null)이면 폴백 3종 그대로, 전부 비개인화', () => {
    const result = deriveSetConcepts([], null);
    expect(result.map((c) => c.slug)).toEqual(FALLBACK_CONCEPTS);
    expect(result.every((c) => !c.personalized)).toBe(true);
  });

  it('SENSITIVE 무고민 — 파생 전용 gentle이 포함되고 라벨이 비어 있지 않다', () => {
    const result = deriveSetConcepts([], 'SENSITIVE');
    const gentle = result.find((c) => c.slug === 'gentle');
    expect(gentle).toBeDefined();
    expect(gentle!.label.length).toBeGreaterThan(0);
  });

  it('사용감 슬러그(fresh·dewy·matte)는 컨셉이 되지 않는다', () => {
    const result = deriveSetConcepts(['fresh', 'dewy', 'matte'], null);
    expect(result.map((c) => c.slug)).toEqual(FALLBACK_CONCEPTS);
  });

  it('모든 경우 정확히 SET_COUNT개, 슬러그 중복 없음', () => {
    const cases: [string[], SkinType | null][] = [
      [[], null],
      [['pore'], 'OILY'],
      [['moisture', 'barrier'], 'DRY'],
      [['pore', 'trouble', 'moisture', 'bright'], 'COMBINATION'],
      [[], 'SENSITIVE'],
    ];
    for (const [concerns, skinType] of cases) {
      const slugs = deriveSetConcepts(concerns, skinType).map((c) => c.slug);
      expect(slugs).toHaveLength(SET_COUNT);
      expect(new Set(slugs).size).toBe(SET_COUNT);
    }
  });

  it('고민 9종 라벨이 프로필 화면(CONCERNS 상수)과 동일 문구다', () => {
    for (const { value, label } of CONCERNS) {
      const result = deriveSetConcepts([value], null);
      expect(result[0].slug).toBe(value);
      expect(result[0].label).toBe(label);
    }
  });
});

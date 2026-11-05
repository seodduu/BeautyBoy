import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { appendRecord, historyKey, readHistory, SKIN_HISTORY_LIMIT } from './history';
import type { SkinRecord } from './types';

function rec(n: number): SkinRecord {
  return {
    v: 1,
    at: new Date(Date.UTC(2026, 10, 5, 0, 0, n)).toISOString(),
    redness: n,
    foreheadL: 60,
    foreheadA: 15,
    cheekA: 16,
    faceWidthRatio: 0.5,
  };
}

beforeEach(() => localStorage.clear());
afterEach(() => vi.restoreAllMocks());

describe('skin history', () => {
  it('키는_회원ID별이다', () => {
    expect(historyKey(7)).toBe('bb.skin.v1.7');
  });

  it('빈_이력은_빈배열', () => {
    expect(readHistory(7)).toEqual([]);
  });

  it('append는_앞에_넣는다', () => {
    appendRecord(7, rec(1));
    appendRecord(7, rec(2));
    const h = readHistory(7);
    expect(h).toHaveLength(2);
    expect(h[0].at).toBe(rec(2).at);
  });

  it('30건_초과는_오래된_것부터_버린다', () => {
    for (let i = 1; i <= SKIN_HISTORY_LIMIT + 1; i++) appendRecord(7, rec(i));
    const h = readHistory(7);
    expect(h).toHaveLength(SKIN_HISTORY_LIMIT);
    expect(h[0].at).toBe(rec(SKIN_HISTORY_LIMIT + 1).at);
  });

  it('손상_JSON은_빈_이력으로_친다', () => {
    localStorage.setItem(historyKey(7), '{broken');
    expect(readHistory(7)).toEqual([]);
  });

  it('원소_하나라도_형태가_다르면_전체를_버린다', () => {
    localStorage.setItem(historyKey(7), JSON.stringify([rec(1), { v: 2 }]));
    expect(readHistory(7)).toEqual([]);
  });

  it('회원이_다르면_섞이지_않는다', () => {
    appendRecord(7, rec(1));
    appendRecord(8, rec(2));
    expect(readHistory(7)).toHaveLength(1);
    expect(readHistory(8)).toHaveLength(1);
    expect(readHistory(7)[0].at).toBe(rec(1).at);
  });

  it('quota_예외는_삼킨다', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('quota', 'QuotaExceededError');
    });
    expect(() => appendRecord(7, rec(1))).not.toThrow();
  });
});

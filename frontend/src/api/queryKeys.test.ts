import { describe, expect, it } from 'vitest';
import { queryKeys } from './queryKeys';

describe('queryKeys', () => {
  it('cart()는 항상 같은 키를 준다', () => {
    expect(queryKeys.cart()).toEqual(['cart']);
  });

  it('wishlist()는 항상 같은 키를 준다', () => {
    expect(queryKeys.wishlist()).toEqual(['wishlist']);
  });

  it('compat()은 상품 번호를 오름차순으로 정렬한다 — 고른 순서가 키를 가르지 않는다', () => {
    expect(queryKeys.compat([2, 1])).toEqual(queryKeys.compat([1, 2]));
  });

  it('compat()은 중복을 제거한다', () => {
    expect(queryKeys.compat([1, 1, 2])).toEqual(queryKeys.compat([1, 2]));
  });

  it('compat()은 숫자로 정렬한다 — 문자열 정렬이면 [2,10]과 [10,2]가 갈린다', () => {
    expect(queryKeys.compat([2, 10])).toEqual(queryKeys.compat([10, 2]));
    expect(queryKeys.compat([2, 10])).toEqual(['compat', '2,10']);
  });

  it('compat()은 빈 배열도 안정적인 키를 준다', () => {
    expect(queryKeys.compat([])).toEqual(['compat', '']);
  });
});

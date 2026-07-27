import { describe, expect, it } from 'vitest';
import type { GoodsListItem, TagView } from '../../types/goods';
import { rankByTexture } from './match';

/**
 * v1의 티어 매칭 테스트(matchByBehavior/matchByProfile)는 그 함수들과 함께 삭제됐다 —
 * 점수 공식·체인의 회귀는 composer.test.ts가 덮는다(루틴 조합기 설계 §7).
 */

describe('rankByTexture — 사용감 tie-break', () => {
  function goods(goodsNo: number, slugs: string[]): GoodsListItem {
    const tags: TagView[] = slugs.map((slug) => ({ name: slug, kind: 'TEXTURE', slug }));
    return {
      goodsNo,
      brandName: '브랜드',
      name: `상품 ${goodsNo}`,
      thumbnailUrl: '',
      listPrice: 10000,
      salePrice: 10000,
      discountRate: 0,
      badges: [],
      rating: 0,
      reviewCount: 0,
      wished: false,
      todayDreamAvailable: false,
      tags,
    };
  }

  it('선호 사용감을 가진 후보가 앞으로 온다', () => {
    const items = [goods(1, []), goods(2, ['dewy']), goods(3, [])];

    expect(rankByTexture(items, ['dewy']).map((i) => i.goodsNo)).toEqual([2, 1, 3]);
  });

  it('일치 개수가 같으면 서버가 준 인기순을 유지한다 — 안정 정렬', () => {
    const items = [goods(1, ['dewy']), goods(2, ['dewy']), goods(3, [])];

    expect(rankByTexture(items, ['dewy']).map((i) => i.goodsNo)).toEqual([1, 2, 3]);
  });

  it('일치 개수가 많을수록 더 앞에 온다', () => {
    const items = [goods(1, ['dewy']), goods(2, ['dewy', 'fresh']), goods(3, [])];

    expect(rankByTexture(items, ['dewy', 'fresh']).map((i) => i.goodsNo)).toEqual([2, 1, 3]);
  });

  it('선호 사용감이 없으면 서버 순서를 그대로 돌려준다', () => {
    const items = [goods(1, []), goods(2, ['dewy'])];

    expect(rankByTexture(items, []).map((i) => i.goodsNo)).toEqual([1, 2]);
  });

  it('원본 배열을 건드리지 않는다', () => {
    const items = [goods(1, []), goods(2, ['dewy'])];

    rankByTexture(items, ['dewy']);

    expect(items.map((i) => i.goodsNo)).toEqual([1, 2]);
  });
});

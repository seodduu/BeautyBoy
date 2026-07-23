import { describe, expect, it } from 'vitest';
import { fetchGoodsDetail, fetchGoodsList } from './goods';

describe('fetchGoodsList — msw 목 서버 대상', () => {
  it('page=1, size=20이면 21~40번째 상품을 반환하고 hasNext=false', async () => {
    const result = await fetchGoodsList({ page: 1, size: 20 });

    expect(result.content).toHaveLength(20);
    expect(result.content[0].goodsNo).toBe(21);
    expect(result.content[19].goodsNo).toBe(40);
    expect(result.totalElements).toBe(40);
    expect(result.hasNext).toBe(false);
  });

  it('sort=priceAsc면 실제 판매가 오름차순으로 정렬된다', async () => {
    const result = await fetchGoodsList({ page: 0, size: 40, sort: 'priceAsc' });

    const prices = result.content.map((item) => item.salePrice);
    const sortedPrices = [...prices].sort((a, b) => a - b);
    expect(prices).toEqual(sortedPrices);
  });

  it('categoryCode=C001은 접두사 필터로 동작해 하위 카테고리까지 포함한다', async () => {
    const result = await fetchGoodsList({ page: 0, size: 40, categoryCode: 'C001' });

    // fixture 배분: C001001*(6) + C001002*(6) + C001003*(6) = 18건
    expect(result.totalElements).toBe(18);
    expect(result.content).toHaveLength(18);
  });

  it('categoryCode=C002(클렌징)는 3개 하위 카테고리를 합쳐 반환한다', async () => {
    const result = await fetchGoodsList({ page: 0, size: 40, categoryCode: 'C002' });

    // C002001(3) + C002002(3) + C002003(2) = 8건
    expect(result.totalElements).toBe(8);
  });

  it('루틴 5단계 categoryCode가 모두 4건 이상을 반환한다(섹션이 비지 않는다)', async () => {
    const routineCodes = ['C002', 'C001001', 'C001002', 'C001003', 'C004001'];

    for (const code of routineCodes) {
      const result = await fetchGoodsList({ page: 0, size: 4, categoryCode: code });
      expect(result.totalElements, `${code}의 상품 수`).toBeGreaterThanOrEqual(4);
      expect(result.content, `${code}의 첫 페이지`).toHaveLength(4);
    }
  });
});

describe('fetchGoodsDetail — msw 목 서버 대상', () => {
  it('존재하는 goodsNo면 해당 상품을 반환한다', async () => {
    const item = await fetchGoodsDetail(1);
    expect(item.goodsNo).toBe(1);
  });

  it('존재하지 않는 goodsNo면 에러가 그대로 던져진다(TanStack Query가 처리)', async () => {
    await expect(fetchGoodsDetail(999999)).rejects.toBeTruthy();
  });
});

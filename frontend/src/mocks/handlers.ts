import { http, HttpResponse } from 'msw';
import { goodsFixtures } from './fixtures/goods';
import type { GoodsFixture } from './fixtures/goods';
import type { ApiEnvelope, PageResponse } from '../types/goods';

/** GET /api/v1/categories/tree 응답용 카테고리 트리. GoodsListItem 계약과 무관한 mock 전용 형태. */
interface CategoryNode {
  code: string;
  name: string;
  children: CategoryNode[];
}

const categoryTree: CategoryNode[] = [
  {
    code: 'C001',
    name: '스킨케어',
    children: [
      { code: 'C0011', name: '토너', children: [] },
      { code: 'C0012', name: '로션', children: [] },
    ],
  },
  {
    code: 'C002',
    name: '헤어',
    children: [{ code: 'C0021', name: '샴푸', children: [] }],
  },
  { code: 'C003', name: '바디', children: [] },
  { code: 'C004', name: '메이크업', children: [] },
];

/** T1 설계 정렬 규약: popular|new|sales|priceAsc|discount. */
function sortGoods(items: GoodsFixture[], sort: string | null): GoodsFixture[] {
  const sorted = [...items];

  switch (sort) {
    case 'new':
      return sorted.sort((a, b) => b.createdAt - a.createdAt);
    case 'sales':
      return sorted.sort((a, b) => b.salesCount - a.salesCount);
    case 'priceAsc':
      return sorted.sort((a, b) => a.salePrice - b.salePrice);
    case 'discount':
      return sorted.sort((a, b) => b.discountRate - a.discountRate);
    case 'popular':
    default:
      // mock에는 별도 인기 지표가 없어 등록 순서를 그대로 인기순으로 간주한다.
      return sorted;
  }
}

export const handlers = [
  http.get('/api/v1/goods', ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? '0');
    const size = Number(url.searchParams.get('size') ?? '20');
    const sort = url.searchParams.get('sort');
    const categoryCode = url.searchParams.get('categoryCode');

    const filtered = categoryCode
      ? goodsFixtures.filter((item) => item.categoryCode.startsWith(categoryCode))
      : goodsFixtures;

    const sorted = sortGoods(filtered, sort);
    const start = page * size;
    const content = sorted.slice(start, start + size);
    const totalElements = sorted.length;
    const totalPages = Math.max(1, Math.ceil(totalElements / size));

    const body: ApiEnvelope<PageResponse<GoodsFixture>> = {
      code: 'OK',
      message: 'success',
      data: {
        content,
        page,
        size,
        totalElements,
        totalPages,
        hasNext: start + size < totalElements,
      },
    };

    return HttpResponse.json(body);
  }),

  http.get('/api/v1/goods/:goodsNo', ({ params }) => {
    const goodsNo = Number(params.goodsNo);
    const found = goodsFixtures.find((item) => item.goodsNo === goodsNo);

    if (!found) {
      return HttpResponse.json(
        { code: 'GOODS_NOT_FOUND', message: '상품을 찾을 수 없습니다.', data: null },
        { status: 404 },
      );
    }

    const body: ApiEnvelope<GoodsFixture> = { code: 'OK', message: 'success', data: found };
    return HttpResponse.json(body);
  }),

  http.get('/api/v1/categories/tree', () => {
    const body: ApiEnvelope<CategoryNode[]> = { code: 'OK', message: 'success', data: categoryTree };
    return HttpResponse.json(body);
  }),
];

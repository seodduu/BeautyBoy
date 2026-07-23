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

/** 실 시드(V12__seed_catalog.sql) 1~2depth를 그대로 옮긴 목 트리. fixture의 categoryCode와 같은 축을 쓴다. */
const categoryTree: CategoryNode[] = [
  {
    code: 'C001',
    name: '스킨케어',
    children: [
      { code: 'C001001', name: '토너/스킨', children: [] },
      { code: 'C001002', name: '에센스/세럼', children: [] },
      { code: 'C001003', name: '로션/크림', children: [] },
    ],
  },
  {
    code: 'C002',
    name: '클렌징',
    children: [
      { code: 'C002001', name: '클렌징폼', children: [] },
      { code: 'C002002', name: '클렌징오일/밤', children: [] },
      { code: 'C002003', name: '필링/스크럽', children: [] },
    ],
  },
  {
    code: 'C003',
    name: '헤어·바디',
    children: [
      { code: 'C003001', name: '샴푸/린스', children: [] },
      { code: 'C003002', name: '바디워시', children: [] },
    ],
  },
  {
    code: 'C004',
    name: '선케어',
    children: [{ code: 'C004001', name: '선크림', children: [] }],
  },
  {
    code: 'C005',
    name: '쉐이빙·그루밍',
    children: [{ code: 'C005001', name: '면도기/날', children: [] }],
  },
  {
    code: 'C006',
    name: '메이크업',
    children: [{ code: 'C006001', name: '베이스메이크업', children: [] }],
  },
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
  /* 인증 목 — mock 모드에서 /main 같은 보호 라우트에 도달하려면 로그인이 성립해야 한다.
     비밀번호를 검증하지 않는다: 목적은 화면 흐름 확인이지 인증 로직 재현이 아니다.
     refresh는 401이 기본값 — 새로고침하면 비로그인으로 시작해 가드가 실제로 동작하는지 보인다.
     세션 복원이 필요한 테스트는 server.use()로 이 핸들러를 덮어쓴다(App.test.tsx가 그렇게 한다). */
  http.post('/api/v1/auth/login', () =>
    HttpResponse.json({
      code: 'OK',
      message: '성공',
      data: { accessToken: 'mock-access-token' },
    }),
  ),

  http.post('/api/v1/auth/signup', () =>
    HttpResponse.json({
      code: 'OK',
      message: '성공',
      data: { id: 1, email: 'mock@beautyboy.dev', nickname: '민수', grade: 'BRONZE' },
    }),
  ),

  http.get('/api/v1/members/me', () =>
    HttpResponse.json({
      code: 'OK',
      message: '성공',
      data: { id: 1, email: 'mock@beautyboy.dev', nickname: '민수', grade: 'BRONZE' },
    }),
  ),

  http.post('/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })),

  http.post('/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })),

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

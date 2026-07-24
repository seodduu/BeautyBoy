import { http, HttpResponse } from 'msw';
import { goodsFixtures } from './fixtures/goods';
import type { GoodsFixture } from './fixtures/goods';
import type { ApiEnvelope, PageResponse } from '../types/goods';
import type { RankingItem } from '../types/ranking';
import type {
  GoodsDescription,
  GoodsDetail,
  GoodsIngredientResponse,
  GoodsOption,
} from '../types/detail';
import type { ReviewItem, ReviewStats, QnaItem } from '../types/review';
import {
  ingredientFixtures,
  maxIrritation,
  maxComedogenic,
  reviewFixtures,
  reviewStatsFixture,
  qnaFixtures,
  popularKeywordFixtures,
} from './fixtures/detail';

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

/** goodsFixture의 categoryCode(하위 depth까지 포함)를 categoryTree의 [대분류, 중분류] 이름으로 바꾼다. */
function resolveCategoryPath(categoryCode: string): string[] {
  for (const top of categoryTree) {
    if (categoryCode.startsWith(top.code)) {
      const child = top.children.find((node) => categoryCode.startsWith(node.code));
      return child ? [top.name, child.name] : [top.name];
    }
  }
  return [];
}

/** 브랜드명마다 안정적인 정수를 만든다 — mock 전용이라 실 브랜드 PK와 일치할 필요는 없다. */
function brandIdFor(brandName: string): number {
  let hash = 0;
  for (let i = 0; i < brandName.length; i += 1) {
    hash = (hash * 31 + brandName.charCodeAt(i)) % 100000;
  }
  return hash + 1;
}

/** 상세 조회용 옵션 1~2건 — 홀수 goodsNo는 대용량 옵션을 하나 더 갖는다. */
function buildOptions(found: GoodsFixture): GoodsOption[] {
  const base: GoodsOption = {
    optionNo: found.goodsNo * 10 + 1,
    name: '기본 옵션',
    addPrice: 0,
    stock: 20,
    soldOut: false,
  };

  if (found.goodsNo % 2 === 0) {
    return [base];
  }

  const large: GoodsOption = {
    optionNo: found.goodsNo * 10 + 2,
    name: '대용량',
    addPrice: 3000,
    stock: 15,
    soldOut: false,
  };
  return [base, large];
}

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

  // 성분 배지 — /goods/:goodsNo 보다 먼저 등록한다. MSW는 등록 순서대로 매칭하므로
  // 뒤에 두면 :goodsNo가 "1/ingredients" 형태까지 통째로 삼켜 이 핸들러가 절대 안 걸린다.
  http.get('/api/v1/goods/:goodsNo/ingredients', ({ params }) => {
    const goodsNo = Number(params.goodsNo);
    const body: ApiEnvelope<GoodsIngredientResponse> = {
      code: 'OK',
      message: 'success',
      data: { goodsNo, ingredients: ingredientFixtures, maxIrritation, maxComedogenic },
    };
    return HttpResponse.json(body);
  }),

  // 설명 본문 — ingredients와 같은 이유로 /goods/:goodsNo 보다 먼저 등록한다.
  http.get('/api/v1/goods/:goodsNo/description', ({ params }) => {
    const goodsNo = Number(params.goodsNo);
    const found = goodsFixtures.find((item) => item.goodsNo === goodsNo);

    if (!found) {
      return HttpResponse.json(
        { code: 'GOODS_NOT_FOUND', message: '상품을 찾을 수 없습니다.', data: null },
        { status: 404 },
      );
    }

    const body: ApiEnvelope<GoodsDescription> = {
      code: 'OK',
      message: 'success',
      data: {
        goodsNo,
        description:
          `${found.name}은(는) 매일 쓰는 사용감에 초점을 맞춘 제품입니다.\n` +
          '세안 후 결을 정돈하고 다음 단계 흡수를 돕도록 구성했으며, 아침저녁 모두 사용할 수 있습니다.',
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

    // 실 백엔드는 GoodsDetailResponse(GoodsDetail)를 내려준다 — GoodsFixture(목록 아이템 + mock 전용 필드)를
    // 그대로 반환하면 summary/options 등 상세 전용 필드가 없어 화면이 undefined를 참조해 터진다.
    const detail: GoodsDetail = {
      goodsNo: found.goodsNo,
      brandName: found.brandName,
      brandId: brandIdFor(found.brandName),
      name: found.name,
      summary: `${found.brandName}의 데일리 케어 제품으로, 자극을 최소화한 성분을 담아 매일 편하게 사용할 수 있습니다.`,
      categoryCode: found.categoryCode,
      categoryPath: resolveCategoryPath(found.categoryCode),
      thumbnailUrl: found.thumbnailUrl,
      listPrice: found.listPrice,
      salePrice: found.salePrice,
      discountRate: found.discountRate,
      badges: found.badges,
      status: 'ON_SALE',
      options: buildOptions(found),
      rating: found.rating,
      reviewCount: found.reviewCount,
      wished: found.wished,
      todayDreamAvailable: found.todayDreamAvailable,
    };

    const body: ApiEnvelope<GoodsDetail> = { code: 'OK', message: 'success', data: detail };
    return HttpResponse.json(body);
  }),

  http.get('/api/v1/categories/tree', () => {
    const body: ApiEnvelope<CategoryNode[]> = { code: 'OK', message: 'success', data: categoryTree };
    return HttpResponse.json(body);
  }),

  /* GET /search — 이름·브랜드에 q가 대소문자 무관 부분일치하면 매치. 실 검색어(예: "향수")는
     fixture 어디에도 없으므로 그대로 두면 자연스럽게 0건이 되어 EmptyState를 화면에서 확인할 수 있다. */
  http.get('/api/v1/search', ({ request }) => {
    const url = new URL(request.url);
    const q = (url.searchParams.get('q') ?? '').toLowerCase();
    const page = Number(url.searchParams.get('page') ?? '0');
    const size = Number(url.searchParams.get('size') ?? '20');
    const sort = url.searchParams.get('sort');

    const matched = goodsFixtures.filter(
      (item) => item.name.toLowerCase().includes(q) || item.brandName.toLowerCase().includes(q),
    );

    const sorted = sortGoods(matched, sort);
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

  /* GET /search/autocomplete — 상품명/브랜드명에서 q를 포함하는 후보를 최대 10개 뽑는다. */
  http.get('/api/v1/search/autocomplete', ({ request }) => {
    const url = new URL(request.url);
    const q = (url.searchParams.get('q') ?? '').toLowerCase();

    const candidates = new Set<string>();
    if (q) {
      for (const item of goodsFixtures) {
        if (item.brandName.toLowerCase().includes(q)) {
          candidates.add(item.brandName);
        }
        if (item.name.toLowerCase().includes(q)) {
          candidates.add(item.name);
        }
        if (candidates.size >= 10) {
          break;
        }
      }
    }

    const body: ApiEnvelope<string[]> = {
      code: 'OK',
      message: 'success',
      data: Array.from(candidates).slice(0, 10),
    };
    return HttpResponse.json(body);
  }),

  http.get('/api/v1/search/popular-keywords', () => {
    const body: ApiEnvelope<string[]> = {
      code: 'OK',
      message: 'success',
      data: popularKeywordFixtures,
    };
    return HttpResponse.json(body);
  }),

  /* GET /rankings — 랭킹은 페이지네이션 없는 바로 List<RankingItem>. score는 순위 역순으로
     내려가는 임의 점수를 부여한다(1위가 가장 높음). */
  http.get('/api/v1/rankings', ({ request }) => {
    const url = new URL(request.url);
    const categoryCode = url.searchParams.get('categoryCode');

    const filtered = categoryCode
      ? goodsFixtures.filter((item) => item.categoryCode.startsWith(categoryCode))
      : goodsFixtures;

    const ranked: RankingItem[] = filtered.slice(0, 20).map((item, index) => ({
      rank: index + 1,
      goodsNo: item.goodsNo,
      brandName: item.brandName,
      name: item.name,
      thumbnailUrl: item.thumbnailUrl,
      listPrice: item.listPrice,
      salePrice: item.salePrice,
      discountRate: item.discountRate,
      score: 1000 - index * 10,
    }));

    const body: ApiEnvelope<RankingItem[]> = { code: 'OK', message: 'success', data: ranked };
    return HttpResponse.json(body);
  }),

  http.get('/api/v1/reviews', ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? '0');
    const size = 20;
    const start = page * size;
    const content = reviewFixtures.slice(start, start + size);
    const totalElements = reviewFixtures.length;

    const body: ApiEnvelope<PageResponse<ReviewItem>> = {
      code: 'OK',
      message: 'success',
      data: {
        content,
        page,
        size,
        totalElements,
        totalPages: Math.max(1, Math.ceil(totalElements / size)),
        hasNext: start + size < totalElements,
      },
    };
    return HttpResponse.json(body);
  }),

  http.get('/api/v1/reviews/stats', () => {
    const body: ApiEnvelope<ReviewStats> = { code: 'OK', message: 'success', data: reviewStatsFixture };
    return HttpResponse.json(body);
  }),

  http.get('/api/v1/qna', ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? '0');
    const size = 20;
    const start = page * size;
    const content = qnaFixtures.slice(start, start + size);
    const totalElements = qnaFixtures.length;

    const body: ApiEnvelope<PageResponse<QnaItem>> = {
      code: 'OK',
      message: 'success',
      data: {
        content,
        page,
        size,
        totalElements,
        totalPages: Math.max(1, Math.ceil(totalElements / size)),
        hasNext: start + size < totalElements,
      },
    };
    return HttpResponse.json(body);
  }),

  // 장바구니 담기 — 성공 토스트 확인용. 실 재고 검증은 서버 몫이라 mock에서는 항상 성공한다.
  http.post('/api/v1/cart/items', () =>
    HttpResponse.json({ code: 'OK', message: 'success', data: null }, { status: 201 }),
  ),
];

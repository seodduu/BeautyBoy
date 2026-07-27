import { http, HttpResponse } from 'msw';
import { goodsFixtures, nextStepFixtures } from './fixtures/goods';
import type { GoodsFixture } from './fixtures/goods';
import type { ApiEnvelope, NextStepBlock, PageResponse } from '../types/goods';
import type { RankingItem } from '../types/ranking';
import type {
  GoodsDescription,
  GoodsDetail,
  GoodsIngredientResponse,
  GoodsOption,
} from '../types/detail';
import type { GoodsAssessment } from '../types/assessment';
import type { ReviewItem, ReviewStats, QnaItem } from '../types/review';
import type { CartItem } from '../api/cart';
import type { CompatCheckResult } from '../api/compat';
import type { Address, AddressInput, ProfileInput } from '../api/member';
import type { OrderCreateRequest, OrderDetail, OrderSummary } from '../api/order';
import type { MyReviewItem } from '../api/review';
import type { RoutineResponse, SkinType } from '../api/routine';
import type { FlowRulesResponse } from '../types/routine';
import type {
  AdminGoodsDetailResponse,
  AdminGoodsListItem,
  AdminGoodsSaveInput,
  AdminQnaResponse,
  AdminRoutineTemplate,
} from '../api/admin';
import { ROUTINE_STEPS } from '../features/routine/steps';
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

/**
 * 장바구니 dev 목 상태 — 모듈 스코프 가변 배열. 수량 변경·삭제가 반영되는 것을
 * 오프라인(VITE_USE_MOCK)에서도 눈으로 확인할 수 있도록 요청마다 재계산한다.
 * (AHA 토너 × 레티노이드 세럼 조합으로 CONFLICT 배너를 기본 노출시켜 궁합 UI를 바로 볼 수 있게 한다.)
 */
let cartItemsFixture: CartItem[] = [
  {
    cartItemId: 1,
    goodsNo: 101,
    optionNo: 11,
    goodsName: 'AHA 각질 토너',
    optionName: '150ml',
    unitPrice: 20000,
    quantity: 2,
    lineAmount: 40000,
  },
  {
    cartItemId: 2,
    goodsNo: 102,
    optionNo: null,
    goodsName: '레티노이드 나이트 세럼',
    optionName: '',
    unitPrice: 32000,
    quantity: 1,
    lineAmount: 32000,
  },
];

function recomputeLineAmount(item: CartItem): CartItem {
  return { ...item, lineAmount: item.unitPrice * item.quantity };
}

/** 배송지 dev 목 상태 — 기본배송지 1건을 미리 심어 자동선택 흐름을 mock에서도 볼 수 있게 한다(Task 4-10). */
let addressesFixture: Address[] = [
  {
    id: 1,
    receiver: '민수',
    phone: '01000000000',
    zipcode: '06236',
    address1: '서울특별시 강남구 테헤란로 1',
    address2: '101동 202호',
    isDefault: true,
  },
];

/**
 * 규칙 배포 mock(GET /routine/flow-rules) — 메인 개인화 Task 3-6.
 *
 * 실 시드(V75__seed_routine_flow_rule.sql · V82__concern_target_rule.sql)에서 루틴 5단계를
 * 겨냥하는 행만 골라 옮겼다. **문구는 여기 한 곳에서만 관리한다** — 화면 컴포넌트는 reason을
 * 하드코딩하지 않는다(next-step 설계 §3의 "reason은 DB가 유일한 출처" 원칙을 mock에서도 지킨다).
 *
 * version은 실 서버라면 두 테이블의 SHA-256 앞 16자다. mock은 내용을 손으로 고치는 자리이므로
 * 고정 문자열을 두고, 픽스처를 바꿀 때 함께 올린다(안 올리면 저장본이 304로 계속 살아남는다).
 */
const flowRulesFixture: FlowRulesResponse = {
  version: 'mock-flowrules-1',
  flowRules: [
    {
      fromCategoryCode: 'C002001',
      fromTagSlug: 'exfoliate',
      toCategoryCode: 'C001001',
      toTagSlug: 'soothe',
      edgeKind: 'BUFFER',
      reason: '피지·각질까지 씻어낸 다음엔 진정 토너로 완충해 주세요',
      priority: 10,
    },
    {
      fromCategoryCode: 'C002001',
      fromTagSlug: null,
      toCategoryCode: 'C001001',
      toTagSlug: 'moisture',
      edgeKind: 'NEXT_STEP',
      reason: '세안 다음 단계는 수분 충전이에요',
      priority: 20,
    },
    {
      fromCategoryCode: 'C001001',
      fromTagSlug: null,
      toCategoryCode: 'C001002',
      toTagSlug: null,
      edgeKind: 'NEXT_STEP',
      reason: '결을 정돈했다면 영양을 채울 차례예요',
      priority: 20,
    },
    {
      fromCategoryCode: 'C001002',
      fromTagSlug: null,
      toCategoryCode: 'C001003',
      toTagSlug: 'moisture',
      edgeKind: 'NEXT_STEP',
      reason: '세럼의 수분을 크림으로 덮어 가두세요',
      priority: 20,
    },
    {
      fromCategoryCode: 'C001003',
      fromTagSlug: null,
      toCategoryCode: 'C004001',
      toTagSlug: 'uv',
      edgeKind: 'NEXT_STEP',
      reason: '아침 루틴의 마지막은 자외선 차단이에요',
      priority: 20,
    },
    {
      fromCategoryCode: 'C004001',
      fromTagSlug: null,
      toCategoryCode: 'C002002',
      toTagSlug: 'cleanse',
      edgeKind: 'PAIRED_REMOVAL',
      reason: '자외선차단제는 클렌징오일로 지워야 남지 않아요',
      priority: 10,
    },
  ],
  concernRules: [
    {
      concernTagSlug: 'pore',
      toCategoryCode: 'C001002',
      toTagSlug: 'pore',
      reason: '모공은 세럼 단계에서 정면으로 잡는 게 효율적이에요',
      priority: 10,
    },
    {
      concernTagSlug: 'pore',
      toCategoryCode: 'C002001',
      toTagSlug: 'pore',
      reason: '모공 관리는 잘 씻어내는 것부터예요',
      priority: 20,
    },
    {
      concernTagSlug: 'moisture',
      toCategoryCode: 'C001003',
      toTagSlug: 'moisture',
      reason: '보습이 고민이라면 덮어 가두는 크림이 핵심이에요',
      priority: 10,
    },
    {
      concernTagSlug: 'moisture',
      toCategoryCode: 'C001002',
      toTagSlug: 'moisture',
      reason: '크림 전에 수분 세럼으로 채워 두세요',
      priority: 20,
    },
    {
      concernTagSlug: 'soothe',
      toCategoryCode: 'C001001',
      toTagSlug: 'soothe',
      reason: '예민한 날엔 진정 토너로 결부터 달래 주세요',
      priority: 10,
    },
    {
      concernTagSlug: 'sebum',
      toCategoryCode: 'C002001',
      toTagSlug: 'sebum',
      reason: '피지가 고민이라면 세안부터 피지 잡는 제품으로',
      priority: 10,
    },
    {
      concernTagSlug: 'gentle',
      toCategoryCode: 'C001001',
      toTagSlug: 'gentle',
      reason: '자극 없는 토너로 결만 정돈해 주세요',
      priority: 20,
    },
  ],
};

/** 이미 승인된 주문번호(mock 전용). 실 서버의 결제 상태 대신 중복 승인만 흉내낸다. */
const confirmedOrderNos = new Set<string>();

/** 마이페이지 찜 dev 목 상태 — goodsFixtures의 1·3번을 미리 찜해둬 화면을 바로 확인할 수 있게 한다(Task 4-13). */
let wishlistFixture: Set<number> = new Set([1, 3]);

/**
 * 마이페이지 주문내역 dev 목 상태(Task 4-13). 목록(OrderSummary)과 상세(OrderDetail)를 각각 둔다 —
 * 실 서버도 두 응답의 필드가 다르다(목록은 대표상품 요약, 상세는 스냅샷 전량).
 * 상세의 배송지(수령인 "박서준"·판교)는 addressesFixture의 현재 배송지(수령인 "민수"·강남)와
 * 의도적으로 다른 값이다 — 마이페이지 상세 화면이 스냅샷을 쓰는지 눈으로도 구분되게 한다.
 */
const myOrdersFixture: OrderSummary[] = [
  {
    orderNo: 'ORD-20260710-0001',
    status: 'PAID',
    representativeGoodsName: goodsFixtures[0].name,
    itemCount: 2,
    payableAmount: 38000,
    orderedAt: '2026-07-10T14:20:00',
  },
  {
    orderNo: 'ORD-20260620-0002',
    status: 'PAID',
    representativeGoodsName: goodsFixtures[3].name,
    itemCount: 1,
    payableAmount: 21000,
    orderedAt: '2026-06-20T09:05:00',
  },
];

const myOrderDetailFixture: Record<string, OrderDetail> = {
  'ORD-20260710-0001': {
    orderNo: 'ORD-20260710-0001',
    status: 'PAID',
    totalAmount: 40000,
    discountAmount: 2000,
    payableAmount: 38000,
    receiverName: '박서준',
    receiverPhone: '01055556666',
    zipcode: '13529',
    address1: '경기도 성남시 분당구 판교역로 235',
    address2: 'H스퀘어 4층',
    deliveryType: 'NORMAL',
    orderedAt: '2026-07-10T14:20:00',
    paidAt: '2026-07-10T14:21:00',
    items: [
      {
        goodsName: goodsFixtures[0].name,
        optionName: '기본',
        unitPrice: 20000,
        quantity: 1,
        lineAmount: 20000,
      },
      {
        goodsName: goodsFixtures[1].name,
        optionName: '기본',
        unitPrice: 20000,
        quantity: 1,
        lineAmount: 20000,
      },
    ],
  },
  'ORD-20260620-0002': {
    orderNo: 'ORD-20260620-0002',
    status: 'PAID',
    totalAmount: 21000,
    discountAmount: 0,
    payableAmount: 21000,
    receiverName: '박서준',
    receiverPhone: '01055556666',
    zipcode: '13529',
    address1: '경기도 성남시 분당구 판교역로 235',
    address2: 'H스퀘어 4층',
    deliveryType: 'NORMAL',
    orderedAt: '2026-06-20T09:05:00',
    paidAt: '2026-06-20T09:06:00',
    items: [
      {
        goodsName: goodsFixtures[3].name,
        optionName: '기본',
        unitPrice: 21000,
        quantity: 1,
        lineAmount: 21000,
      },
    ],
  },
};

/** 마이페이지 내 리뷰 dev 목 상태(Task 4-13). backend MyReviewItem과 필드를 1:1로 맞춘다. */
const myReviewsFixture: MyReviewItem[] = [
  {
    reviewId: 1001,
    goodsNo: goodsFixtures[0].goodsNo,
    goodsName: goodsFixtures[0].name,
    thumbnailUrl: goodsFixtures[0].thumbnailUrl,
    rating: 4.5,
    content: '자극 없이 순하게 쓰고 있어요. 재구매 의사 있습니다.',
    helpfulCount: 12,
    createdAt: '2026-07-15T11:00:00',
  },
  {
    reviewId: 1002,
    goodsNo: goodsFixtures[3].goodsNo,
    goodsName: goodsFixtures[3].name,
    thumbnailUrl: goodsFixtures[3].thumbnailUrl,
    rating: 4.0,
    content: '향이 은은하고 흡수가 빨라요.',
    helpfulCount: 4,
    createdAt: '2026-06-25T20:30:00',
  },
];

/**
 * 관리자 상품 목록 dev 목 상태(Task 4-14). goodsFixtures에 status만 덧붙여 재사용한다.
 * goodsNo 2는 HIDDEN으로 고정해 관리자 화면에서 "숨김" 배지를 바로 확인할 수 있게 한다.
 * KNOWN GAP(api/admin.ts 문서 주석 참고): 실 서버 GoodsListItem에는 status가 없다 — 이 mock에서만
 * 존재하는 필드다.
 */
let adminGoodsFixture: AdminGoodsListItem[] = goodsFixtures.map((item) => ({
  ...item,
  status: item.goodsNo === 2 ? 'HIDDEN' : 'ON_SALE',
}));

/** 관리자 루틴 템플릿 dev 목 상태(Task 4-14). ROUTINE_STEPS 5단계 + goodsFixtures로 조립한다. */
const adminRoutineTemplatesFixture: AdminRoutineTemplate[] = [
  {
    templateId: 1,
    name: '복합성 기본 루틴',
    skinType: 'COMBINATION',
    timeSlot: 'BASIC',
    description: '피부타입에 맞춰 고른 기본 5단계입니다.',
    steps: ROUTINE_STEPS.map((step) => ({
      stepOrder: step.order,
      stepName: step.label,
      beginnerTip: step.copy,
      goodsNos: goodsFixtures
        .filter((item) => item.categoryCode.startsWith(step.categoryCode))
        .slice(0, 3)
        .map((item) => item.goodsNo),
    })),
  },
];

/**
 * 관리자 문의 목록 dev 목 상태(Task 4-14b). qnaFixtures(공개 목록과 같은 원본)에 goodsNo만
 * 얹어서 admin 전용 응답 모양(AdminQnaResponse)으로 재조립한다 — question은 원본 그대로
 * 마스킹 없이 낸다(실 서버 QnaService.adminList와 동일하게 admin은 비밀글 본문을 그대로 본다).
 */
const adminQnaFixture: AdminQnaResponse[] = qnaFixtures.map((item, index) => ({
  ...item,
  goodsNo: goodsFixtures[index % goodsFixtures.length].goodsNo,
}));

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

  // 마이페이지 프로필 탭이 skinType/concerns/ageBand을 그리므로 실 Me 형태에 맞춰 채운다(Task 4-13).
  // role: 'ADMIN'(Task 4-14) — 오프라인 mock은 페르소나가 하나뿐이라 admin 화면도 같은 로그인으로
  // 확인할 수 있게 관리자로 고정한다. 실 서버는 회원마다 실제 role을 내려준다.
  http.get('/api/v1/members/me', () =>
    HttpResponse.json({
      code: 'OK',
      message: '성공',
      data: {
        id: 1,
        email: 'mock@beautyboy.dev',
        nickname: '민수',
        grade: 'BRONZE',
        skinType: 'COMBINATION',
        // 프로필 태그 교체(V81) 이후의 어휘 — tag.slug와 같은 소문자 슬러그다. 구 어휘('PORE')를
        // 두면 effectiveConcerns가 통째로 버리고 피부타입 파생으로 내려가 mock만 다르게 움직인다.
        concerns: ['pore', 'dewy'],
        ageBand: '20s',
        role: 'ADMIN',
      },
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
    const tag = url.searchParams.get('tag');

    const filtered = goodsFixtures
      .filter((item) => (categoryCode ? item.categoryCode.startsWith(categoryCode) : true))
      .filter((item) => (tag ? item.tags.some((t) => t.slug === tag) : true));

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

  // 성분 종합판정 — /goods/:goodsNo 보다 먼저 등록한다(위 ingredients 주석 참조).
  http.get('/api/v1/goods/:goodsNo/assessment', ({ params }) => {
    const goodsNo = Number(params.goodsNo);
    const body: ApiEnvelope<GoodsAssessment> = {
      code: 'OK',
      message: 'success',
      data: {
        goodsNo,
        verdictCode: 'MOSTLY_FINE',
        verdictText: '대체로 무난해요',
        checkCount: 2,
        rinseOff: true,
        flagged: [
          { ingredientId: 5, name: '살리실산', inciName: 'salicylic acid', summary: '모공 속 노폐물 정리에 강한 BHA', flags: ['EXFOLIANT_ACID', 'LIMIT'], axis: 'CHECK', acidClass: 'BHA', limitText: '* 배합한도 : <보존제> 살리실릭애씨드로서 0.5%' },
          { ingredientId: 19, name: '리모넨', inciName: 'limonene', summary: '시트러스향에 흔한 향료 성분', flags: ['ALLERGEN'], axis: 'CHECK', acidClass: null, limitText: null },
          { ingredientId: 28, name: '토코페롤', inciName: 'tocopherol', summary: '항산화 작용을 하는 비타민E', flags: ['LIMIT'], axis: 'INFO', acidClass: null, limitText: '* 배합한도 : 기타 제품에 20%' },
        ],
      },
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

  // 추천 상품 — ingredients/description과 같은 이유로 /goods/:goodsNo 보다 먼저 등록한다.
  // 자기 자신을 제외한 fixture 4건을 내려준다(페이지네이션 없음).
  http.get('/api/v1/goods/:goodsNo/recommended', ({ params }) => {
    const goodsNo = Number(params.goodsNo);
    const recommended = goodsFixtures.filter((item) => item.goodsNo !== goodsNo).slice(0, 4);

    const body: ApiEnvelope<GoodsFixture[]> = { code: 'OK', message: 'success', data: recommended };
    return HttpResponse.json(body);
  }),

  // 다음 단계 추천 — recommended와 같은 이유로 /goods/:goodsNo 보다 먼저 등록한다.
  // 문구·블록 구성은 fixtures/goods.ts의 nextStepFixtures가 유일한 출처(Task 7 하드코딩 금지 원칙).
  http.get('/api/v1/goods/:goodsNo/next-step', ({ params }) => {
    const goodsNo = Number(params.goodsNo);
    const blocks: NextStepBlock[] = nextStepFixtures[goodsNo] ?? [];

    const body: ApiEnvelope<{ blocks: NextStepBlock[] }> = {
      code: 'OK',
      message: 'success',
      data: { blocks },
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
      tags: found.tags,
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

  // 관리자 문의 전체 목록 — Task 4-14b. AdminQnaController(GET /admin/qna)를 그대로 매핑.
  // 공개 /qna와 달리 goodsNo 필터가 없고 question이 마스킹되지 않는다.
  http.get('/api/v1/admin/qna', ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? '0');
    const size = 20;
    const start = page * size;
    const content = adminQnaFixture.slice(start, start + size);
    const totalElements = adminQnaFixture.length;

    const body: ApiEnvelope<PageResponse<AdminQnaResponse>> = {
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

  // 장바구니 조회/수정/삭제 — Task 4-9.
  http.get('/api/v1/cart/items', () =>
    HttpResponse.json({ code: 'OK', message: 'success', data: cartItemsFixture }),
  ),

  http.patch('/api/v1/cart/items/:cartItemId', async ({ params, request }) => {
    const cartItemId = Number(params.cartItemId);
    const { quantity } = (await request.json()) as { quantity: number };
    cartItemsFixture = cartItemsFixture.map((item) =>
      item.cartItemId === cartItemId ? recomputeLineAmount({ ...item, quantity }) : item,
    );
    return HttpResponse.json({ code: 'OK', message: 'success', data: null });
  }),

  http.delete('/api/v1/cart/items/:cartItemId', ({ params }) => {
    const cartItemId = Number(params.cartItemId);
    cartItemsFixture = cartItemsFixture.filter((item) => item.cartItemId !== cartItemId);
    return HttpResponse.json({ code: 'OK', message: 'success', data: null });
  }),

  http.post('/api/v1/cart/items/bulk', () =>
    HttpResponse.json({ code: 'OK', message: 'success', data: null }, { status: 201 }),
  ),

  // 궁합 체크 — 목 goodsNo 101(AHA)·102(레티노이드) 조합이면 CONFLICT를 낸다.
  http.post('/api/v1/compat/check', async ({ request }) => {
    const { goodsNos } = (await request.json()) as { goodsNos: number[] };
    const hasConflictPair = goodsNos.includes(101) && goodsNos.includes(102);

    const result: CompatCheckResult = hasConflictPair
      ? {
          overall: 'CONFLICT',
          findings: [
            {
              verdict: 'CONFLICT',
              categoryA: 'AHA',
              categoryB: '레티노이드',
              reason: '두 성분 모두 각질과 피부 턴오버를 촉진해 함께 쓰면 자극이 중첩돼요.',
              goodsNos: [101, 102],
            },
          ],
        }
      : { overall: 'OK', findings: [] };

    return HttpResponse.json({ code: 'OK', message: 'success', data: result });
  }),

  // 루틴 가이드 — Task 4-12. skinType×time 단순 룩업(설계 8장 "1차: 템플릿 매칭").
  // steps.ts(ROUTINE_STEPS)의 5단계 매핑을 그대로 써서 카테고리별 fixture를 추천으로 묶는다.
  http.get('/api/v1/routines', ({ request }) => {
    const url = new URL(request.url);
    const skinType = (url.searchParams.get('skinType') as SkinType | null) ?? 'COMBINATION';
    const time = url.searchParams.get('time') ?? 'BASIC';

    const body: ApiEnvelope<RoutineResponse> = {
      code: 'OK',
      message: 'success',
      data: {
        templateId: 1,
        name: `${skinType} 기본 루틴`,
        skinType,
        time,
        description: '피부타입에 맞춰 고른 기본 5단계입니다. 추천은 단계마다 첫 번째가 기본 선택돼요.',
        steps: ROUTINE_STEPS.map((step) => ({
          stepOrder: step.order,
          stepName: step.label,
          beginnerTip: step.copy,
          recommendations: goodsFixtures
            .filter((item) => item.categoryCode.startsWith(step.categoryCode))
            .slice(0, 3),
        })),
      },
    };

    return HttpResponse.json(body);
  }),

  // 규칙 배포 — 메인 개인화 Task 3-6. 실 서버(FlowRuleController)와 같은 ETag 계약을 흉내낸다:
  // If-None-Match가 version과 같으면 본문 없이 304. 기기 측 캐시(features/affinity/flowRules.ts)의
  // 재방문 경로를 오프라인에서도 그대로 밟게 하려는 것이다.
  http.get('/api/v1/routine/flow-rules', ({ request }) => {
    const ifNoneMatch = request.headers.get('If-None-Match')?.replace(/^W\/|"/g, '');
    if (ifNoneMatch === flowRulesFixture.version) {
      return new HttpResponse(null, { status: 304 });
    }
    return HttpResponse.json(
      { code: 'OK', message: 'success', data: flowRulesFixture },
      { headers: { ETag: flowRulesFixture.version } },
    );
  }),

  // 프로필 수정 — Task 4-12(로컬 퀴즈 결과 승격). 실 서버 응답은 소비하지 않으므로 데이터 없이 OK만 낸다.
  http.put('/api/v1/members/me/profile', async ({ request }) => {
    await (request.json() as Promise<ProfileInput>);
    return HttpResponse.json({ code: 'OK', message: 'success', data: null });
  }),

  // 배송지 조회/등록 — Task 4-10.
  http.get('/api/v1/members/me/addresses', () =>
    HttpResponse.json({ code: 'OK', message: 'success', data: addressesFixture }),
  ),

  http.post('/api/v1/members/me/addresses', async ({ request }) => {
    const body = (await request.json()) as AddressInput;
    const created: Address = { id: addressesFixture.length + 1, ...body };
    addressesFixture = [...addressesFixture, created];
    return HttpResponse.json({ code: 'OK', message: 'success', data: created }, { status: 201 });
  }),

  // 배송지 수정(기본배송지 지정 겸용) — updateAddress Javadoc(api/member.ts) 참고: 전용 엔드포인트가
  // 없어 이 PUT에 isDefault를 실어 보낸다(Task 4-13). "기본으로 설정"이면 나머지를 전부 false로 내린다
  // — 실 서버는 DB 유니크 제약(4-2)이 이를 보장하지만, mock은 배열이라 직접 흉내낸다.
  http.put('/api/v1/members/me/addresses/:id', async ({ params, request }) => {
    const id = Number(params.id);
    const body = (await request.json()) as AddressInput;
    addressesFixture = addressesFixture.map((address) => {
      if (address.id === id) {
        return { id, ...body };
      }
      return body.isDefault ? { ...address, isDefault: false } : address;
    });
    return HttpResponse.json({ code: 'OK', message: 'success', data: null });
  }),

  http.delete('/api/v1/members/me/addresses/:id', ({ params }) => {
    const id = Number(params.id);
    addressesFixture = addressesFixture.filter((address) => address.id !== id);
    return new HttpResponse(null, { status: 204 });
  }),

  // 찜 조회/등록/해제 — Task 4-13. 백엔드 WishlistItemResponse처럼 goodsNo만 내려준다.
  http.get('/api/v1/wishlist', () =>
    HttpResponse.json({
      code: 'OK',
      message: 'success',
      data: Array.from(wishlistFixture).map((goodsNo) => ({ goodsNo })),
    }),
  ),

  http.post('/api/v1/wishlist/:goodsNo', ({ params }) => {
    wishlistFixture.add(Number(params.goodsNo));
    return HttpResponse.json({ code: 'OK', message: 'success', data: null }, { status: 201 });
  }),

  http.delete('/api/v1/wishlist/:goodsNo', ({ params }) => {
    wishlistFixture.delete(Number(params.goodsNo));
    return new HttpResponse(null, { status: 204 });
  }),

  // 내 리뷰 — Task 4-13.
  http.get('/api/v1/reviews/me', ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? '0');
    const size = 20;
    const start = page * size;
    const content = myReviewsFixture.slice(start, start + size);
    const totalElements = myReviewsFixture.length;

    const body: ApiEnvelope<PageResponse<MyReviewItem>> = {
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

  // 주문 상세 — Task 4-13. /orders 목록보다 먼저 등록할 필요는 없다(경로 형태가 겹치지 않는다).
  http.get('/api/v1/orders/:orderNo', ({ params }) => {
    const detail = myOrderDetailFixture[String(params.orderNo)];
    if (!detail) {
      return HttpResponse.json(
        { code: 'ORDER_NOT_FOUND', message: '주문을 찾을 수 없습니다.', data: null },
        { status: 404 },
      );
    }
    return HttpResponse.json({ code: 'OK', message: 'success', data: detail });
  }),

  // 주문 생성 — Task 4-10. payableAmount는 장바구니 lineAmount 합으로 mock 계산한다
  // (실 서버는 항상 재고·가격을 재검증해 다시 계산한다 — 이 mock은 오프라인 화면 확인용).
  http.post('/api/v1/orders', async ({ request }) => {
    const body = (await request.json()) as OrderCreateRequest;
    const payableAmount = cartItemsFixture.reduce((sum, item) => sum + item.lineAmount, 0);
    return HttpResponse.json(
      {
        code: 'OK',
        message: 'success',
        data: { orderNo: `ORD-MOCK-${Date.now()}`, payableAmount, deliveryType: body.deliveryType },
      },
      { status: 201 },
    );
  }),

  // 마이페이지 주문내역 목록 — Task 4-13 이전에는 빈 배열 고정이었다. 목 dev 데이터(myOrdersFixture)로
  // 채워 목록·상세 화면을 실제로 확인할 수 있게 한다.
  http.get('/api/v1/orders', () =>
    HttpResponse.json({ code: 'OK', message: 'success', data: myOrdersFixture }),
  ),

  // 결제 승인 — Task 4-11(성공/실패 화면)이 소비하지만, 목 스택 일관성을 위해 여기서 함께 등록한다.
  // 같은 주문번호로 두 번 들어오면 실 서버처럼 PAYMENT_ALREADY_CONFIRMED로 막는다 — 완료 화면이
  // 승인을 정확히 한 번만 보내는지(StrictMode 이중 마운트 포함) 브라우저에서도 드러나게 하려는 것이다.
  // 금액 불일치(PAYMENT_AMOUNT_MISMATCH)는 목이 주문 금액을 보관하지 않아 판정할 수 없으므로
  // 재현하지 않는다 — 그 경로는 유닛테스트가 msw 오버라이드로 덮는다.
  http.post('/api/v1/payments/confirm', async ({ request }) => {
    const { orderNo, amount } = (await request.json()) as { orderNo: string; amount: number };

    if (confirmedOrderNos.has(orderNo)) {
      return HttpResponse.json(
        { code: 'PAYMENT_ALREADY_CONFIRMED', message: '이미 승인된 결제입니다', detail: null },
        { status: 409 },
      );
    }
    confirmedOrderNos.add(orderNo);

    return HttpResponse.json({
      code: 'OK',
      message: 'success',
      data: { orderNo, status: 'PAID', paidAmount: amount },
    });
  }),

  // 리뷰 작성 — Task 4-14. goodsNo 102(레티노이드 세럼)는 REVIEW_NOT_PURCHASED 흐름을
  // 화면에서 바로 확인할 수 있도록 고의로 거절한다. 그 외에는 성공 처리한다.
  http.post('/api/v1/reviews', async ({ request }) => {
    const body = (await request.json()) as { goodsNo: number; rating: number; content: string };
    if (body.goodsNo === 102) {
      return HttpResponse.json(
        { code: 'REVIEW_NOT_PURCHASED', message: '구매한 상품에만 리뷰를 쓸 수 있습니다', data: null },
        { status: 403 },
      );
    }
    return HttpResponse.json({ code: 'OK', message: 'success', data: null }, { status: 201 });
  }),

  http.post('/api/v1/reviews/:reviewId/helpful', () =>
    HttpResponse.json({ code: 'OK', message: 'success', data: null }),
  ),

  // 문의 작성 — Task 4-14.
  http.post('/api/v1/qna', () =>
    HttpResponse.json({ code: 'OK', message: 'success', data: null }, { status: 201 }),
  ),

  // 관리자 상품 관리 — Task 4-14. AdminGoodsController(GET/POST/PUT/DELETE /admin/goods)를 그대로 매핑.
  http.get('/api/v1/admin/goods', ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get('q');
    const page = Number(url.searchParams.get('page') ?? '0');
    const size = Number(url.searchParams.get('size') ?? '20');

    const filtered = q ? adminGoodsFixture.filter((item) => item.name.includes(q)) : adminGoodsFixture;
    const start = page * size;
    const content = filtered.slice(start, start + size);
    const totalElements = filtered.length;

    const body: ApiEnvelope<PageResponse<AdminGoodsListItem>> = {
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

  // 관리자 상품 상세(인라인 수정 진입) — Task 4-14a/4-14b. HIDDEN도 조회된다(admin 전용 상세).
  http.get('/api/v1/admin/goods/:goodsNo', ({ params }) => {
    const goodsNo = Number(params.goodsNo);
    const found = adminGoodsFixture.find((item) => item.goodsNo === goodsNo);
    const categoryCode = goodsFixtures.find((item) => item.goodsNo === goodsNo)?.categoryCode ?? '';

    if (!found) {
      return HttpResponse.json(
        { code: 'GOODS_NOT_FOUND', message: '상품을 찾을 수 없습니다.', data: null },
        { status: 404 },
      );
    }

    const detail: AdminGoodsDetailResponse = {
      goodsNo: found.goodsNo,
      brandId: brandIdFor(found.brandName),
      categoryCode,
      name: found.name,
      summary: `${found.brandName}의 데일리 케어 제품으로, 자극을 최소화한 성분을 담아 매일 편하게 사용할 수 있습니다.`,
      thumbnailUrl: found.thumbnailUrl,
      listPrice: found.listPrice,
      salePrice: found.salePrice,
      status: found.status,
    };

    const body: ApiEnvelope<AdminGoodsDetailResponse> = { code: 'OK', message: 'success', data: detail };
    return HttpResponse.json(body);
  }),

  http.post('/api/v1/admin/goods', async ({ request }) => {
    const body = (await request.json()) as AdminGoodsSaveInput;
    const nextGoodsNo = Math.max(0, ...adminGoodsFixture.map((item) => item.goodsNo)) + 1;
    adminGoodsFixture = [
      ...adminGoodsFixture,
      {
        goodsNo: nextGoodsNo,
        brandName: `브랜드 #${body.brandId}`,
        name: body.name,
        thumbnailUrl: body.thumbnailUrl,
        listPrice: body.listPrice,
        salePrice: body.salePrice,
        discountRate: body.listPrice > 0 ? Math.round((1 - body.salePrice / body.listPrice) * 100) : 0,
        badges: [],
        rating: 0,
        reviewCount: 0,
        wished: false,
        todayDreamAvailable: false,
        status: 'ON_SALE',
      },
    ];
    return HttpResponse.json({ code: 'OK', message: 'success', data: nextGoodsNo }, { status: 201 });
  }),

  http.put('/api/v1/admin/goods/:goodsNo', async ({ params, request }) => {
    const goodsNo = Number(params.goodsNo);
    const body = (await request.json()) as AdminGoodsSaveInput;
    adminGoodsFixture = adminGoodsFixture.map((item) =>
      item.goodsNo === goodsNo
        ? {
            ...item,
            name: body.name,
            thumbnailUrl: body.thumbnailUrl || item.thumbnailUrl,
            listPrice: body.listPrice,
            salePrice: body.salePrice,
            status: (body.status as AdminGoodsListItem['status']) ?? item.status,
          }
        : item,
    );
    return HttpResponse.json({ code: 'OK', message: 'success', data: null });
  }),

  http.delete('/api/v1/admin/goods/:goodsNo', ({ params }) => {
    const goodsNo = Number(params.goodsNo);
    adminGoodsFixture = adminGoodsFixture.map((item) =>
      item.goodsNo === goodsNo ? { ...item, status: 'HIDDEN' } : item,
    );
    return HttpResponse.json({ code: 'OK', message: 'success', data: null });
  }),

  // 관리자 루틴 관리 — Task 4-14. AdminRoutineController(GET /admin/routines,
  // PUT /admin/routines/{templateId}/steps/{stepOrder}/goods)를 그대로 매핑.
  http.get('/api/v1/admin/routines', () => {
    const body: ApiEnvelope<AdminRoutineTemplate[]> = {
      code: 'OK',
      message: 'success',
      data: adminRoutineTemplatesFixture,
    };
    return HttpResponse.json(body);
  }),

  http.put('/api/v1/admin/routines/:templateId/steps/:stepOrder/goods', async ({ params, request }) => {
    const templateId = Number(params.templateId);
    const stepOrder = Number(params.stepOrder);
    const { goodsNos } = (await request.json()) as { goodsNos: number[] };

    const template = adminRoutineTemplatesFixture.find((t) => t.templateId === templateId);
    const step = template?.steps.find((s) => s.stepOrder === stepOrder);
    if (step) {
      step.goodsNos = goodsNos;
    }
    return HttpResponse.json({ code: 'OK', message: 'success', data: null });
  }),

  // 관리자 문의 답변 — Task 4-14. AdminQnaController(POST /admin/qna/{qnaId}/answer)를 그대로 매핑.
  http.post('/api/v1/admin/qna/:qnaId/answer', ({ params }) => {
    const qnaId = Number(params.qnaId);
    const item = qnaFixtures.find((q) => q.qnaId === qnaId);
    if (item) {
      item.status = 'ANSWERED';
    }
    // adminQnaFixture는 qnaFixtures를 스프레드해 만든 별도 객체이므로 참조가 다르다 —
    // admin 목록도 같이 갱신해야 답변 후 화면이 "답변완료"로 바뀐다.
    const adminItem = adminQnaFixture.find((q) => q.qnaId === qnaId);
    if (adminItem) {
      adminItem.status = 'ANSWERED';
    }
    return HttpResponse.json({ code: 'OK', message: 'success', data: null });
  }),
];

import type { BadgeType, GoodsListItem, TagView } from '../../types/goods';

/**
 * T1 시드와 성격을 맞춘 상품 fixture. `GoodsListItem` 계약은 그대로 지키되,
 * 목록 정렬·필터 mock 구현에만 필요한 필드(categoryCode/createdAt/salesCount)를 확장한다.
 * 이 확장 필드는 mock 전용이며 `GoodsListItem` 계약에는 없다 — 실 API 연동(Wave 3) 시 폐기된다.
 */
export interface GoodsFixture extends GoodsListItem {
  /** 카테고리 코드. 접두사 매칭으로 하위 카테고리까지 포함해 필터링한다(예: "C001" → "C0011", "C0012" 포함). */
  categoryCode: string;
  /** sort=new 계산용 등록 시각(epoch ms). Wave 1 백엔드는 실제 생성일을 내려주지만 mock은 인덱스로 흉내낸다. */
  createdAt: number;
  /** sort=sales 계산용 판매량. Wave 1 백엔드 계약에는 없는 mock 전용 값이다. */
  salesCount: number;
}

/** 오프라인·CI에서도 깨지지 않도록 외부 이미지 없이 로컬 SVG 데이터 URI로 썸네일을 만든다. */
function svgThumbnail(label: string, hue: number): string {
  // 실제 제품 사진이 들어오기 전까지는 무채색으로 통일한다 — 파스텔 색이 모노톤 무드를 깬다.
  // hue는 호출부 시그니처 유지를 위해 남기되 명도만 미세하게 흔들어 카드가 완전히 똑같아 보이지 않게 한다.
  const tone = 86 + (hue % 3) * 2; // 86·88·90% 사이
  const bg = `hsl(0, 0%, ${tone}%)`;
  const fg = `hsl(0, 0%, 45%)`;
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="240" height="240">` +
    `<rect width="240" height="240" fill="${bg}"/>` +
    `<text x="120" y="128" font-family="sans-serif" font-size="22" fill="${fg}" text-anchor="middle">${label}</text>` +
    `</svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

const BRANDS = [
  '어반메일',
  '포맨랩',
  '그루밍코드',
  '블랙베럴',
  '바버스톤',
  '넘버식스',
  '드라이독',
  '스킨아머',
];

/**
 * 실 시드(backend V12__seed_catalog.sql)의 카테고리 코드를 그대로 쓴다.
 * mock 전용 코드를 따로 두면 루틴 매핑(features/routine/steps.ts)이 mock에서만 빈 화면이 된다.
 * 루틴 5단계(C002 / C001001 / C001002 / C001003 / C004001)는 각각 4건 이상이 되도록 배분했다.
 */
const CATEGORY_PLAN: Array<{ code: string; label: string; count: number }> = [
  // 루틴 1단계 — 클렌징(C002)
  { code: 'C002001', label: '클렌징폼', count: 3 },
  { code: 'C002002', label: '클렌징오일', count: 3 },
  { code: 'C002003', label: '필링젤', count: 2 },
  // 루틴 2단계 — 토너/스킨(C001001)
  { code: 'C001001001', label: '수분토너', count: 3 },
  { code: 'C001001002', label: '진정토너', count: 3 },
  // 루틴 3단계 — 에센스/세럼(C001002)
  { code: 'C001002001', label: '고보습에센스', count: 3 },
  { code: 'C001002002', label: '미백세럼', count: 3 },
  // 루틴 4단계 — 로션/크림(C001003)
  { code: 'C001003001', label: '데일리로션', count: 3 },
  { code: 'C001003002', label: '고영양크림', count: 3 },
  // 루틴 5단계 — 선크림(C004001)
  { code: 'C004001', label: '선크림', count: 5 },
  // 루틴 축 밖 — 목록/필터 검증용
  { code: 'C003001', label: '샴푸', count: 3 },
  { code: 'C003002', label: '바디워시', count: 2 },
  { code: 'C005001', label: '면도기', count: 2 },
  { code: 'C006001', label: '베이스메이크업', count: 2 },
];

const BADGE_PLAN: BadgeType[][] = [
  [],
  ['SALE'],
  ['COUPON'],
  ['GIFT'],
  ['ONE_PLUS_ONE'],
  ['SALE', 'COUPON'],
  ['SALE', 'GIFT', 'ONE_PLUS_ONE'],
  [],
];

const DISCOUNT_PLAN = [0, 10, 15, 0, 20, 25, 0, 30, 40, 5];

/**
 * 태그 샘플. 실 백엔드는 상품마다 최대 여러 개를 규칙 파생 + 수동 보정으로 채우지만(V71 시드),
 * mock은 목록/상세/카드 화면을 눈으로 확인할 수 있을 정도의 조합 몇 가지만 순환시킨다.
 * 빈 배열도 섞어 tags가 없는 상품에서 레이아웃이 무너지지 않는지 항상 검증되게 한다.
 */
const TAG_PLAN: TagView[][] = [
  [],
  [{ name: '진정', kind: 'EFFECT', slug: 'soothing' }, { name: '산뜻함', kind: 'TEXTURE', slug: 'fresh' }],
  [{ name: '보습', kind: 'EFFECT', slug: 'moisture' }],
  [],
  [
    { name: '세정', kind: 'EFFECT', slug: 'cleanse' },
    { name: '모공케어', kind: 'EFFECT', slug: 'pore-care' },
    { name: '산뜻함', kind: 'TEXTURE', slug: 'fresh' },
  ],
  [{ name: '자외선차단', kind: 'EFFECT', slug: 'uv-protect' }, { name: '가벼운제형', kind: 'TEXTURE', slug: 'light' }],
];

function buildGoodsFixtures(): GoodsFixture[] {
  const items: GoodsFixture[] = [];
  let goodsNo = 1;
  let hue = 12;

  for (const { code, label, count } of CATEGORY_PLAN) {
    for (let i = 0; i < count; i++) {
      const brand = BRANDS[(goodsNo - 1) % BRANDS.length];
      const discountRate = DISCOUNT_PLAN[(goodsNo - 1) % DISCOUNT_PLAN.length];
      const listPrice = 12000 + ((goodsNo - 1) % 12) * 2500;
      const salePrice = Math.round((listPrice * (100 - discountRate)) / 100 / 100) * 100;

      items.push({
        goodsNo,
        brandName: brand,
        name: `${brand} ${label} No.${goodsNo} 남성용 데일리 케어`,
        thumbnailUrl: svgThumbnail(`${label} ${i + 1}`, hue),
        listPrice,
        salePrice,
        discountRate,
        badges: BADGE_PLAN[(goodsNo - 1) % BADGE_PLAN.length],
        // Wave 1 백엔드가 실제로 내려주는 기본값 — 리뷰 기능이 붙기 전까지 전부 0이다.
        rating: 0,
        reviewCount: 0,
        wished: false,
        todayDreamAvailable: goodsNo % 4 === 0,
        tags: TAG_PLAN[(goodsNo - 1) % TAG_PLAN.length],
        categoryCode: code,
        createdAt: Date.now() - goodsNo * 86_400_000,
        salesCount: (goodsNo * 37) % 500,
      });

      goodsNo += 1;
      hue = (hue + 47) % 360;
    }
  }

  return items;
}

export const goodsFixtures: GoodsFixture[] = buildGoodsFixtures();

import type { BadgeType, GoodsListItem, NextStepBlock, TagView } from '../../types/goods';

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
 * sort=review(리뷰 많은 순) 확인용 리뷰수. 전부 0이면 목에서 정렬이 눈에 보이지 않는다
 * (salesCount를 mock 전용으로 주입한 것과 같은 이유). 0을 섞어 둬야 `Rating`의 빈 상태
 * "첫 리뷰를 기다려요"도 목록 화면에서 계속 검증된다.
 */
const REVIEW_COUNT_PLAN = [0, 12, 3, 87, 0, 41, 6, 152, 24, 0, 68];

/** 리뷰가 있는 상품에만 붙는 평점. 리뷰수 0이면 평점도 0이다(둘이 갈라지면 카드가 거짓말한다). */
const RATING_PLAN = [4.6, 4.2, 3.9, 4.8, 4.0, 3.5, 4.4];

/**
 * 태그 샘플. 실 백엔드는 상품마다 최대 여러 개를 규칙 파생 + 수동 보정으로 채우지만(V72 시드),
 * mock은 목록/상세/카드 화면을 눈으로 확인할 수 있을 정도의 조합 몇 가지만 순환시킨다.
 * 빈 배열도 섞어 tags가 없는 상품에서 레이아웃이 무너지지 않는지 항상 검증되게 한다.
 *
 * slug·name·kind는 실제 V72 태그 마스터 18종과 정확히 맞춘다:
 * EFFECT — cleanse(세정) exfoliate(각질 케어) sebum(피지 관리) soothe(진정) moisture(보습)
 *          uv(자외선차단) bright(브라이트닝) firm(탄력) anti-aging(안티에이징) scalp(두피 케어)
 *          pore(모공 케어) trouble(트러블 케어) barrier(장벽 케어) antioxidant(항산화)
 * PROPERTY — gentle(저자극)
 * TEXTURE — fresh(산뜻함) dewy(촉촉함) matte(매트)
 */
const TAG_PLAN: TagView[][] = [
  [],
  [{ name: '진정', kind: 'EFFECT', slug: 'soothe' }, { name: '산뜻함', kind: 'TEXTURE', slug: 'fresh' }],
  [{ name: '보습', kind: 'EFFECT', slug: 'moisture' }],
  [],
  [
    { name: '세정', kind: 'EFFECT', slug: 'cleanse' },
    { name: '피지 관리', kind: 'EFFECT', slug: 'sebum' },
    { name: '산뜻함', kind: 'TEXTURE', slug: 'fresh' },
  ],
  [{ name: '자외선차단', kind: 'EFFECT', slug: 'uv' }, { name: '매트', kind: 'TEXTURE', slug: 'matte' }],
  // 태그확장(V72) 신규 slug 샘플 — 모공/트러블 케어 + 저자극(PROPERTY), 9개까지 쌓아
  // 상세 화면 flex-wrap 줄바꿈을 눈으로 확인할 수 있게 한다.
  [
    { name: '모공 케어', kind: 'EFFECT', slug: 'pore' },
    { name: '트러블 케어', kind: 'EFFECT', slug: 'trouble' },
    { name: '피지 관리', kind: 'EFFECT', slug: 'sebum' },
    { name: '각질 케어', kind: 'EFFECT', slug: 'exfoliate' },
    { name: '진정', kind: 'EFFECT', slug: 'soothe' },
    { name: '항산화', kind: 'EFFECT', slug: 'antioxidant' },
    { name: '장벽 케어', kind: 'EFFECT', slug: 'barrier' },
    { name: '저자극', kind: 'PROPERTY', slug: 'gentle' },
    { name: '산뜻함', kind: 'TEXTURE', slug: 'fresh' },
  ],
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
      const reviewCount = REVIEW_COUNT_PLAN[(goodsNo - 1) % REVIEW_COUNT_PLAN.length];

      items.push({
        goodsNo,
        brandName: brand,
        name: `${brand} ${label} No.${goodsNo} 남성용 데일리 케어`,
        thumbnailUrl: svgThumbnail(`${label} ${i + 1}`, hue),
        listPrice,
        salePrice,
        discountRate,
        badges: BADGE_PLAN[(goodsNo - 1) % BADGE_PLAN.length],
        rating: reviewCount === 0 ? 0 : RATING_PLAN[(goodsNo - 1) % RATING_PLAN.length],
        reviewCount,
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

/**
 * "다음 단계" 슬롯 mock 픽스처(GET /goods/:goodsNo/next-step, Task 6).
 * 실 서버는 routine_flow_rule.reason을 그대로 실어 나른다 — mock도 문구를 여기 한 곳에서만 관리하고
 * 컴포넌트(Task 7)는 이 문구를 하드코딩하지 않는다.
 * - goods 2(클렌징폼) → BUFFER 1블록. 설계 §6 시드 예시("각질 케어 다음엔 진정으로 완충")를
 *   화면 톤에 맞춰 문구만 다듬었다.
 * - goods 21(데일리로션) → NEXT_STEP(수분 마무리) + PAIRED_REMOVAL(선케어 클렌징) 2블록.
 * - 그 외 goodsNo → 빈 blocks(섹션 미노출 케이스를 mock에서도 재현).
 */
export const nextStepFixtures: Record<number, NextStepBlock[]> = {
  2: [
    {
      edgeKind: 'BUFFER',
      reason: '각질 토너 다음 단계는 진정 세럼으로 완충하세요',
      items: goodsFixtures.filter((item) => item.goodsNo === 4),
    },
  ],
  21: [
    {
      edgeKind: 'NEXT_STEP',
      reason: '보습을 마쳤다면 자외선차단으로 마무리하세요',
      items: goodsFixtures.filter((item) => item.goodsNo >= 27 && item.goodsNo <= 30),
    },
    {
      edgeKind: 'PAIRED_REMOVAL',
      reason: '자외선차단제는 오일로 지워야 남지 않아요',
      items: goodsFixtures.filter((item) => item.goodsNo >= 4 && item.goodsNo <= 6),
    },
  ],
};

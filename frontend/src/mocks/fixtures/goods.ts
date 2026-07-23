import type { BadgeType, GoodsListItem } from '../../types/goods';

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
  const bg = `hsl(${hue}, 35%, 88%)`;
  const fg = `hsl(${hue}, 40%, 38%)`;
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

/** 카테고리 트리(요약) — 총 40건을 이 배분대로 채운다. C001 접두사 필터 테스트가 이 배분에 의존한다. */
const CATEGORY_PLAN: Array<{ code: string; label: string; count: number }> = [
  { code: 'C001', label: '스킨케어', count: 5 },
  { code: 'C0011', label: '토너', count: 5 },
  { code: 'C0012', label: '로션', count: 5 },
  { code: 'C002', label: '헤어', count: 5 },
  { code: 'C0021', label: '샴푸', count: 5 },
  { code: 'C003', label: '바디', count: 10 },
  { code: 'C004', label: '메이크업', count: 5 },
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

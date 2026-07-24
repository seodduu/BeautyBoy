import type { IngredientBadge } from '../../types/detail';
import type { ReviewItem, ReviewStats, QnaItem } from '../../types/review';

/**
 * 상세 페이지(성분/리뷰/Q&A) mock 전용 fixture.
 * goodsFixtures와 달리 상품별로 갈라 만들 필요가 없어 — 어느 goodsNo로 조회해도
 * 같은 목 데이터를 재사용한다(실 API 연동 전까지 화면 흐름 확인이 목적이므로 충분하다).
 */

/** 자극도 4(높음) 1건을 포함해 배지 톤(주의/위험)이 실제로 보이게 구성한다. */
export const ingredientFixtures: IngredientBadge[] = [
  {
    ingredientId: 1,
    name: '정제수',
    category: '베이스',
    irritationLevel: 0,
    comedogenic: 0,
    summary: '피부 자극이 거의 없는 기본 베이스 성분입니다.',
    key: false,
  },
  {
    ingredientId: 2,
    name: '나이아신아마이드',
    category: '기능성',
    irritationLevel: 1,
    comedogenic: 0,
    summary: '피부 톤 개선에 도움을 주는 저자극 기능성 성분입니다.',
    key: true,
  },
  {
    ingredientId: 3,
    name: '변성알코올',
    category: '용제',
    irritationLevel: 4,
    comedogenic: 1,
    summary: '휘발성이 강해 민감성 피부는 자극을 느낄 수 있는 고자극 성분입니다.',
    key: true,
  },
  {
    ingredientId: 4,
    name: '판테놀',
    category: '진정',
    irritationLevel: 0,
    comedogenic: 0,
    summary: '피부 진정과 보습에 도움을 주는 저자극 성분입니다.',
    key: false,
  },
];

export const maxIrritation = Math.max(...ingredientFixtures.map((i) => i.irritationLevel));
export const maxComedogenic = Math.max(...ingredientFixtures.map((i) => i.comedogenic));

export const reviewFixtures: ReviewItem[] = [
  {
    reviewId: 1,
    memberId: 101,
    rating: 5,
    content: '보습력이 정말 좋아서 겨울에도 당김이 없어요. 재구매 의사 있습니다.',
    skinType: '건성',
    helpfulCount: 12,
    createdAt: '2026-06-01T09:00:00Z',
  },
  {
    reviewId: 2,
    memberId: 102,
    rating: 4,
    content: '자극 없이 순하게 쓰기 좋습니다. 향이 조금 강한 편이에요.',
    skinType: '민감성',
    helpfulCount: 5,
    createdAt: '2026-06-05T13:20:00Z',
  },
  {
    reviewId: 3,
    memberId: 103,
    rating: 3,
    content: '무난하게 쓰고 있는데 드라마틱한 효과는 잘 모르겠어요.',
    skinType: '지성',
    helpfulCount: 2,
    createdAt: '2026-06-10T18:45:00Z',
  },
  {
    reviewId: 4,
    memberId: 104,
    rating: 5,
    content: '트러블이 확실히 줄었어요. 남자 피부에도 잘 맞는 제품인 듯합니다.',
    skinType: '복합성',
    helpfulCount: 8,
    createdAt: '2026-06-15T07:10:00Z',
  },
];

export const reviewStatsFixture: ReviewStats = {
  reviewCount: reviewFixtures.length,
  averageRating:
    Math.round((reviewFixtures.reduce((sum, r) => sum + r.rating, 0) / reviewFixtures.length) * 10) / 10,
};

export const qnaFixtures: QnaItem[] = [
  {
    qnaId: 1,
    question: '유통기한이 어떻게 되나요?',
    isSecret: false,
    status: 'ANSWERED',
    createdAt: '2026-05-20T10:00:00Z',
  },
  {
    qnaId: 2,
    question: '민감성 피부인데 사용해도 괜찮을까요?',
    isSecret: false,
    status: 'ANSWERED',
    createdAt: '2026-05-22T11:30:00Z',
  },
  {
    qnaId: 3,
    question: '결제 관련 개인정보 문의입니다.',
    isSecret: true,
    status: 'WAITING',
    createdAt: '2026-05-25T15:00:00Z',
  },
];

/** 인기 검색어 — 실 남성 화장품 카테고리에서 흔히 검색될 법한 키워드 8종. */
export const popularKeywordFixtures: string[] = [
  '토너',
  '클렌징폼',
  '선크림',
  '세럼',
  '로션',
  '샴푸',
  '면도기',
  '올인원',
];

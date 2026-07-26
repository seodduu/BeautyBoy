export type BadgeType = 'SALE' | 'COUPON' | 'GIFT' | 'ONE_PLUS_ONE';

/** 태그 종류. EFFECT(효과)가 표시 우선순위가 높고, TEXTURE(사용감)은 후순위다. */
export type TagKind = 'EFFECT' | 'TEXTURE';

/**
 * 상품 태그 — 백엔드 TagView(backend/src/main/java/com/beautyboy/catalog/dto/TagView.java)와
 * 필드를 1:1로 맞춘다. 목록/상세/추천 응답 맨 뒤 필드로 실린다.
 */
export interface TagView {
  name: string;
  kind: TagKind;
  slug: string;
}

export interface GoodsListItem {
  goodsNo: number;
  brandName: string;
  name: string;
  thumbnailUrl: string;
  listPrice: number;
  salePrice: number;
  discountRate: number;
  badges: BadgeType[];
  rating: number;
  reviewCount: number;
  wished: boolean;
  todayDreamAvailable: boolean;
  /**
   * 옵션 필드 — Wave 태그 기능 이전 화면(랭킹·검색·마이위시리스트 등)의 기존 리터럴은
   * 이 필드 없이도 계속 컴파일된다. 없으면 빈 배열로 취급한다.
   */
  tags?: TagView[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
}

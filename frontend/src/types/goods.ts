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
  /** 백엔드가 목록/상세/추천 응답 맨 뒤에 항상 배열(빈 배열 포함)로 싣는다 — optional이 아니다. */
  tags: TagView[];
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

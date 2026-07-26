export type BadgeType = 'SALE' | 'COUPON' | 'GIFT' | 'ONE_PLUS_ONE';

/**
 * 태그 종류. 표시 우선순위는 EFFECT(효과) → PROPERTY(속성, 예: 저자극) → TEXTURE(사용감) 순.
 * PROPERTY는 태그확장(V72)에서 추가됐다 — 성분 매핑 유무가 아니라 "저자극"처럼 상품 속성 자체를
 * 나타내는 파생 기준이 달라 EFFECT와 분리했다.
 */
export type TagKind = 'EFFECT' | 'PROPERTY' | 'TEXTURE';

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

/** 다음 단계 추천 블록의 관계 종류. 백엔드 routine_flow_rule.kind와 1:1(NextStepBlock 참고). */
export type EdgeKind = 'NEXT_STEP' | 'PAIRED_REMOVAL' | 'BUFFER';

/**
 * 다음 단계 추천 한 블록 — 백엔드 NextStepBlock
 * (backend/src/main/java/com/beautyboy/routine/dto/NextStepBlock.java)과 필드를 1:1로 맞춘다.
 * reason은 routine_flow_rule.reason 원문이 유일한 출처 — 프론트가 문구를 하드코딩하지 않는다.
 */
export interface NextStepBlock {
  edgeKind: EdgeKind;
  reason: string;
  /** 블록당 최대 4개. GoodsListItem은 동결 계약이므로 그대로 싣는다. */
  items: GoodsListItem[];
}

export interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
}

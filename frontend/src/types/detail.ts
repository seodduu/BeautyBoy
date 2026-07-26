import type { BadgeType, TagView } from './goods';

/**
 * 백엔드 GoodsOptionResponse와 필드를 1:1로 맞춘 옵션 타입.
 * (backend/src/main/java/com/beautyboy/catalog/dto/GoodsOptionResponse.java)
 */
export interface GoodsOption {
  optionNo: number;
  name: string;
  addPrice: number;
  stock: number;
  soldOut: boolean;
}

/**
 * 백엔드 GoodsDetailResponse와 필드를 1:1로 맞춘 상세 타입.
 * 목록 아이템(GoodsListItem)과 필드명을 의도적으로 일치시킨 서버 설계를 그대로 따른다.
 */
export interface GoodsDetail {
  goodsNo: number;
  brandName: string;
  brandId: number;
  name: string;
  summary: string;
  categoryCode: string;
  categoryPath: string[];
  thumbnailUrl: string;
  listPrice: number;
  salePrice: number;
  discountRate: number;
  badges: BadgeType[];
  status: string;
  options: GoodsOption[];
  rating: number;
  reviewCount: number;
  wished: boolean;
  todayDreamAvailable: boolean;
  /** 옵션 필드 — GoodsListItem.tags와 동일한 이유로 옵션이다. 없으면 빈 배열로 취급한다. */
  tags?: TagView[];
}

/**
 * GET /goods/:goodsNo/description 응답 — PDP 지연 로딩 3분할 중 무거운 본문 조각.
 * 상세 응답(GoodsDetail)에는 description이 없고 이 엔드포인트에서만 내려온다.
 * (backend/src/main/java/com/beautyboy/catalog/dto/GoodsDescriptionResponse.java)
 */
export interface GoodsDescription {
  goodsNo: number;
  description: string;
}

/** 성분 배지 — GET /goods/:goodsNo/ingredients 응답의 개별 원소. */
export interface IngredientBadge {
  ingredientId: number;
  name: string;
  category: string;
  irritationLevel: number;
  comedogenic: number;
  summary: string;
  key: boolean;
}

/** GET /goods/:goodsNo/ingredients 응답 전체 — 상세 페이지가 max* 필드를 함께 쓰므로 래퍼째로 반환한다. */
export interface GoodsIngredientResponse {
  goodsNo: number;
  ingredients: IngredientBadge[];
  maxIrritation: number;
  maxComedogenic: number;
}

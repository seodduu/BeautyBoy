export type BadgeType = 'SALE' | 'COUPON' | 'GIFT' | 'ONE_PLUS_ONE';

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

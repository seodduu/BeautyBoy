import { api } from './client';
import type { ApiEnvelope, GoodsListItem, PageResponse } from '../types/goods';

/** T1 설계의 정렬 규약 — mock(handlers.ts)과 실 API가 공유하는 값 집합. */
export type GoodsSort = 'popular' | 'new' | 'sales' | 'priceAsc' | 'discount';

export interface FetchGoodsListParams {
  page?: number;
  size?: number;
  sort?: GoodsSort;
  categoryCode?: string;
}

/** GET /goods — 정렬·페이지네이션·카테고리 필터. ApiEnvelope를 벗겨 PageResponse만 반환한다. */
export async function fetchGoodsList(
  params: FetchGoodsListParams = {},
): Promise<PageResponse<GoodsListItem>> {
  const response = await api.get<ApiEnvelope<PageResponse<GoodsListItem>>>('/goods', { params });
  return response.data.data;
}

/** GET /goods/:goodsNo — 404 등 에러는 그대로 던져 TanStack Query가 처리하게 한다. */
export async function fetchGoodsDetail(goodsNo: number): Promise<GoodsListItem> {
  const response = await api.get<ApiEnvelope<GoodsListItem>>(`/goods/${goodsNo}`);
  return response.data.data;
}

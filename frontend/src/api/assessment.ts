import { api } from './client';
import type { ApiEnvelope } from '../types/goods';
import type { GoodsAssessment } from '../types/assessment';

/** GET /goods/:goodsNo/assessment — 성분 종합판정. 404 등은 그대로 던져 TanStack Query가 처리. */
export async function fetchAssessment(goodsNo: number): Promise<GoodsAssessment> {
  const response = await api.get<ApiEnvelope<GoodsAssessment>>(`/goods/${goodsNo}/assessment`);
  return response.data.data;
}

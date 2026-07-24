import { api } from './client';
import type { ApiEnvelope, PageResponse } from '../types/goods';
import type { QnaItem } from '../types/review';

/** GET /qna — 읽기 전용 문의 목록. */
export async function fetchQna(
  goodsNo: number,
  params: { page?: number } = {},
): Promise<PageResponse<QnaItem>> {
  const response = await api.get<ApiEnvelope<PageResponse<QnaItem>>>('/qna', {
    params: { goodsNo, ...params },
  });
  return response.data.data;
}

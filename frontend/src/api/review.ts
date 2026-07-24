import { api } from './client';
import type { ApiEnvelope, PageResponse } from '../types/goods';
import type { ReviewItem, ReviewStats } from '../types/review';

/** GET /reviews — 읽기 전용. 백엔드에 sort/photoOnly 파라미터가 없어 page만 받는다. */
export async function fetchReviews(
  goodsNo: number,
  params: { page?: number } = {},
): Promise<PageResponse<ReviewItem>> {
  const response = await api.get<ApiEnvelope<PageResponse<ReviewItem>>>('/reviews', {
    params: { goodsNo, ...params },
  });
  return response.data.data;
}

/** GET /reviews/stats — 리뷰 개수·평균 평점 요약. */
export async function fetchReviewStats(goodsNo: number): Promise<ReviewStats> {
  const response = await api.get<ApiEnvelope<ReviewStats>>('/reviews/stats', {
    params: { goodsNo },
  });
  return response.data.data;
}

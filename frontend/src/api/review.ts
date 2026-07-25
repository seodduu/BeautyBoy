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

/**
 * GET /reviews/me 응답의 개별 리뷰 — "내가 쓴 글" 관점이라 상품 카드가 아닌 상품명·썸네일만
 * 곁들인다(backend MyReviewItem.java와 필드를 1:1로 맞춤).
 */
export interface MyReviewItem {
  reviewId: number;
  goodsNo: number;
  goodsName: string;
  thumbnailUrl: string;
  rating: number;
  content: string;
  helpfulCount: number;
  createdAt: string;
}

/** GET /reviews/me — 마이페이지 "내 리뷰" 탭. */
export async function fetchMyReviews(page = 0): Promise<PageResponse<MyReviewItem>> {
  const response = await api.get<ApiEnvelope<PageResponse<MyReviewItem>>>('/reviews/me', {
    params: { page },
  });
  return response.data.data;
}

/** POST /reviews 요청 바디 — backend ReviewCreateRequest와 필드를 1:1로 맞춘다. */
export interface ReviewCreateInput {
  goodsNo: number;
  rating: number;
  content: string;
}

/**
 * POST /reviews — 리뷰 작성. 인증 필요(SecurityConfig anyRequest().authenticated()).
 * 구매하지 않은 상품이면 서버가 REVIEW_NOT_PURCHASED(403)로 거절한다 — 호출부가 그 메시지를
 * 그대로 사용자에게 보여준다(project law: 돈·재고·구매이력 검증은 서버가 최종 판정한다).
 */
export async function createReview(input: ReviewCreateInput): Promise<void> {
  await api.post('/reviews', input);
}

/** POST /reviews/{reviewId}/helpful — 도움돼요 표시. */
export async function markHelpful(reviewId: number): Promise<void> {
  await api.post(`/reviews/${reviewId}/helpful`);
}

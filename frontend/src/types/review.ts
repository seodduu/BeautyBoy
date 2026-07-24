/** GET /reviews 응답의 개별 리뷰 아이템. */
export interface ReviewItem {
  reviewId: number;
  memberId: number;
  rating: number;
  content: string;
  skinType: string;
  helpfulCount: number;
  createdAt: string;
}

/** GET /reviews/stats 응답. */
export interface ReviewStats {
  reviewCount: number;
  averageRating: number;
}

/** GET /qna 응답의 개별 문의 아이템. */
export interface QnaItem {
  qnaId: number;
  question: string;
  isSecret: boolean;
  status: string;
  createdAt: string;
}

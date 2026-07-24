import { useQuery } from '@tanstack/react-query';
import { fetchReviewStats, fetchReviews } from '../../api/review';
import { EmptyState } from '../common/EmptyState';
import { Skeleton } from '../ui/Skeleton';
import './ReviewList.css';

interface ReviewListProps {
  goodsNo: number;
  /** 리뷰 탭이 활성 상태일 때만 true — 비활성 탭에서는 fetch를 막는다(enabled). */
  active: boolean;
}

/** YYYY.MM.DD 포맷. DESIGN.md 접근성 규약: 날짜는 항상 이 포맷으로 통일한다. */
function formatDate(iso: string): string {
  const date = new Date(iso);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}.${month}.${day}`;
}

/**
 * 리뷰 탭 — fetchReviewStats(평균/개수) + fetchReviews(목록)를 조합한다.
 * 탭이 열렸을 때만(enabled: active) 요청하는 lazy fetch.
 */
export function ReviewList({ goodsNo, active }: ReviewListProps) {
  const statsQuery = useQuery({
    queryKey: ['review-stats', goodsNo],
    queryFn: () => fetchReviewStats(goodsNo),
    enabled: active,
  });
  const listQuery = useQuery({
    queryKey: ['reviews', goodsNo],
    queryFn: () => fetchReviews(goodsNo),
    enabled: active,
  });

  if (!active) {
    return null;
  }

  if (statsQuery.isLoading || listQuery.isLoading) {
    return (
      <div className="bb-review-list" aria-hidden="true">
        <Skeleton ratio="6 / 1" />
        <Skeleton ratio="6 / 1" />
      </div>
    );
  }

  if (statsQuery.isError || listQuery.isError) {
    return <p className="bb-review-list__error">리뷰를 불러오지 못했어요.</p>;
  }

  const stats = statsQuery.data;
  const reviews = listQuery.data?.content ?? [];

  if (reviews.length === 0) {
    return <EmptyState title="아직 등록된 리뷰가 없어요" />;
  }

  return (
    <div className="bb-review-list">
      {stats && (
        <p className="bb-review-list__stats">
          평균 {stats.averageRating.toFixed(1)} · 리뷰 {stats.reviewCount.toLocaleString('ko-KR')}개
        </p>
      )}
      <ul className="bb-review-list__items">
        {reviews.map((review) => (
          <li key={review.reviewId} className="bb-review-list__item">
            <div className="bb-review-list__meta">
              <span className="bb-review-list__rating">{review.rating.toFixed(1)}</span>
              <span className="bb-review-list__skin">{review.skinType}</span>
              <span className="bb-review-list__date">{formatDate(review.createdAt)}</span>
            </div>
            <p className="bb-review-list__content">{review.content}</p>
            <p className="bb-review-list__helpful">
              도움돼요 {review.helpfulCount.toLocaleString('ko-KR')}
            </p>
          </li>
        ))}
      </ul>
    </div>
  );
}

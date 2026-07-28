import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchMyReviews } from '../../api/review';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';
import { Skeleton } from '../../components/ui/Skeleton';
import './MyReviews.css';

/** YYYY.MM.DD 포맷 — ReviewList와 같은 날짜 표기 규약(DESIGN.md 접근성 규약). */
function formatDate(iso: string): string {
  const date = new Date(iso);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}.${month}.${day}`;
}

/** 마이페이지 내 리뷰 `/mypage/reviews` — 내가 쓴 리뷰를 상품명·썸네일과 함께 보여준다. */
export function MyReviews() {
  const reviewsQuery = useQuery({ queryKey: ['myReviews'], queryFn: () => fetchMyReviews() });

  if (reviewsQuery.isLoading) {
    return (
      <div className="bb-my-reviews">
        <Skeleton ratio="16 / 5" />
      </div>
    );
  }

  if (reviewsQuery.isError) {
    return (
      <div className="bb-my-reviews">
        <ErrorState title="내 리뷰를 불러오지 못했어요" onRetry={() => reviewsQuery.refetch()} />
      </div>
    );
  }

  const reviews = reviewsQuery.data?.content ?? [];

  if (reviews.length === 0) {
    return (
      <div className="bb-my-reviews">
        <EmptyState title="아직 작성한 리뷰가 없어요" description="구매한 상품에 리뷰를 남겨보세요." />
      </div>
    );
  }

  return (
    <ul className="bb-my-reviews__list">
      {reviews.map((review) => (
        <li key={review.reviewId} className="bb-my-reviews__item">
          <Link to={`/goods/${review.goodsNo}`} className="bb-my-reviews__thumb-link">
            <img
              src={review.thumbnailUrl}
              alt={review.goodsName}
              loading="lazy"
              className="bb-my-reviews__thumb"
            />
          </Link>
          <div className="bb-my-reviews__body">
            <Link to={`/goods/${review.goodsNo}`} className="bb-my-reviews__goods-name">
              {review.goodsName}
            </Link>
            <div className="bb-my-reviews__meta">
              <span className="bb-my-reviews__rating">{review.rating.toFixed(1)}</span>
              <span className="bb-my-reviews__date">{formatDate(review.createdAt)}</span>
            </div>
            <p className="bb-my-reviews__content">{review.content}</p>
            <p className="bb-my-reviews__helpful">
              도움돼요 {review.helpfulCount.toLocaleString('ko-KR')}
            </p>
          </div>
        </li>
      ))}
    </ul>
  );
}

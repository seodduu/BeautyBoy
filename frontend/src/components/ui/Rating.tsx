import './Rating.css';

interface RatingProps {
  rating: number;
  reviewCount: number;
}

/**
 * DESIGN.md "평점 줄" — 별 글리프 1개 + 점수 소수 1자리 + (리뷰수), {typography.meta} / {colors.graphite}.
 * 별 5개를 그리지 않는다(카드 밀도에 비해 과함). 빈 상태는 "첫 리뷰를 기다려요" —
 * "리뷰 없음"은 죽은 정보처럼 읽히므로 쓰지 않는다. 두 상태 모두 자리를 유지한다(높이 안정성).
 */
export function Rating({ rating, reviewCount }: RatingProps) {
  if (reviewCount === 0) {
    return <span className="bb-rating bb-rating--empty">첫 리뷰를 기다려요</span>;
  }

  return (
    <span className="bb-rating">
      <span aria-hidden="true">★</span> {rating.toFixed(1)} (
      {reviewCount.toLocaleString('ko-KR')})
    </span>
  );
}

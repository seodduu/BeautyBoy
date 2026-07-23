import './Rating.css';

interface RatingProps {
  rating: number;
  reviewCount: number;
}

/**
 * DESIGN.md typography.meta / colors.ash.
 * reviewCount===0이면 "리뷰 없음". 접근성 텍스트에 수치를 포함한다.
 */
export function Rating({ rating, reviewCount }: RatingProps) {
  if (reviewCount === 0) {
    return <span className="bb-rating bb-rating--empty">리뷰 없음</span>;
  }

  return (
    <span className="bb-rating">
      {rating.toFixed(1)} ({reviewCount.toLocaleString('ko-KR')})
    </span>
  );
}

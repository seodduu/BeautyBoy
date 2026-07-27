import type { SearchResultItem } from '../../types/search';
import { EmptyState } from '../common/EmptyState';
import { GoodsCard } from './GoodsCard';
import { GoodsCardSkeleton } from './GoodsCardSkeleton';
import './GoodsGrid.css';

interface GoodsGridProps {
  /**
   * SearchResultItem(tags optional)을 받는다 — GoodsListItem(tags required)도 구조적으로
   * 호환되므로 목록/랭킹/추천/루틴/위시리스트 등 tags가 항상 있는 화면도 그대로 넘길 수 있다.
   */
  items: SearchResultItem[];
  onWishToggle?: (goodsNo: number) => void;
  loading?: boolean;
  skeletonCount?: number;
  /**
   * 화면 문맥의 카테고리 코드 — 카드에 그대로 통과시킨다(GoodsCard.categoryCode 주석 참고).
   * 그리드 자체는 이 값을 쓰지 않는다. 찜 버튼이 카드 안에만 있어 전달 통로가 여기뿐이다.
   */
  categoryCode?: string;
}

/**
 * DESIGN.md 상품 그리드 사양: 1440px 5열 / 1024px 4열 / 768px 3열 / 640px 2열.
 * 열 간격 {spacing.lg}, 행 간격 {spacing.xl}. 구분선 없음.
 */
export function GoodsGrid({
  items,
  onWishToggle = () => {},
  loading = false,
  skeletonCount = 10,
  categoryCode,
}: GoodsGridProps) {
  if (loading) {
    return (
      <div className="bb-goods-grid">
        {Array.from({ length: skeletonCount }).map((_, index) => (
          <GoodsCardSkeleton key={index} />
        ))}
      </div>
    );
  }

  if (items.length === 0) {
    return <EmptyState title="표시할 상품이 없어요" description="다른 조건으로 다시 찾아보세요" />;
  }

  return (
    <div className="bb-goods-grid">
      {items.map((item) => (
        <GoodsCard
          key={item.goodsNo}
          item={item}
          onWishToggle={onWishToggle}
          categoryCode={categoryCode}
        />
      ))}
    </div>
  );
}

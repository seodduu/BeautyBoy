import type { GoodsListItem } from '../../types/goods';
import { EmptyState } from '../common/EmptyState';
import { GoodsCard } from './GoodsCard';
import { GoodsCardSkeleton } from './GoodsCardSkeleton';
import './GoodsGrid.css';

interface GoodsGridProps {
  items: GoodsListItem[];
  onWishToggle?: (goodsNo: number) => void;
  loading?: boolean;
  skeletonCount?: number;
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
        <GoodsCard key={item.goodsNo} item={item} onWishToggle={onWishToggle} />
      ))}
    </div>
  );
}

import { Skeleton } from '../ui/Skeleton';
import './GoodsCardSkeleton.css';

/**
 * GoodsCard와 같은 크기의 스켈레톤.
 * 썸네일(1:1) + 브랜드/상품명/가격/평점 자리의 텍스트 바를 GoodsCard와 동일한 간격으로 배치한다.
 */
export function GoodsCardSkeleton() {
  return (
    <div className="bb-goods-card-skeleton" aria-hidden="true">
      <Skeleton ratio="1 / 1" />
      <div className="bb-goods-card-skeleton__line bb-goods-card-skeleton__line--brand" />
      <div className="bb-goods-card-skeleton__line bb-goods-card-skeleton__line--name" />
      <div className="bb-goods-card-skeleton__line bb-goods-card-skeleton__line--name-2" />
      <div className="bb-goods-card-skeleton__line bb-goods-card-skeleton__line--price" />
      <div className="bb-goods-card-skeleton__line bb-goods-card-skeleton__line--rating" />
    </div>
  );
}

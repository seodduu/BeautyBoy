import { useQuery } from '@tanstack/react-query';
import { fetchRecommended } from '../../api/goods';
import { GoodsGrid } from './GoodsGrid';
import './RecommendedSection.css';

interface RecommendedSectionProps {
  goodsNo: number;
}

/**
 * "함께 보면 좋은 상품" — GET /goods/:goodsNo/recommended.
 * 추천은 없을 수 있는 것이지 사용자가 뭔가 해야 하는 상태가 아니므로,
 * 응답이 빈 배열이면 EmptyState를 넣지 않고 섹션 자체를 렌더하지 않는다.
 */
export function RecommendedSection({ goodsNo }: RecommendedSectionProps) {
  const recommendedQuery = useQuery({
    queryKey: ['goods-recommended', goodsNo],
    queryFn: () => fetchRecommended(goodsNo),
  });

  if (recommendedQuery.isLoading) {
    return (
      <section className="bb-recommended">
        <h2 className="bb-recommended__title">함께 보면 좋은 상품</h2>
        <GoodsGrid items={[]} loading skeletonCount={4} />
      </section>
    );
  }

  const items = recommendedQuery.data ?? [];

  if (items.length === 0) {
    return null;
  }

  return (
    <section className="bb-recommended">
      <h2 className="bb-recommended__title">함께 보면 좋은 상품</h2>
      <GoodsGrid items={items} />
    </section>
  );
}

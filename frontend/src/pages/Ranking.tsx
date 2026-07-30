import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { fetchRanking } from '../api/ranking';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorState } from '../components/common/ErrorState';
import { GoodsCard } from '../components/goods/GoodsCard';
import { GoodsCardSkeleton } from '../components/goods/GoodsCardSkeleton';
import '../components/goods/GoodsGrid.css';
import { CategoryTabs } from '../components/ranking/CategoryTabs';
import { useWishToggle } from '../features/wishlist/useWishToggle';
import { useTitle } from '../hooks/useTitle';
import type { GoodsListItem } from '../types/goods';
import type { RankingItem } from '../types/ranking';
import './Ranking.css';

/**
 * RankingItem → GoodsListItem 변환.
 * 랭킹 API는 badges/rating/reviewCount/wished/todayDreamAvailable을 내려주지 않으므로
 * (설계상 랭킹 산정에 필요한 필드만 응답) GoodsCard가 요구하는 나머지 필드는 중립값으로 채운다.
 */
function toGoodsListItem(item: RankingItem): GoodsListItem {
  return {
    goodsNo: item.goodsNo,
    brandName: item.brandName,
    name: item.name,
    thumbnailUrl: item.thumbnailUrl,
    listPrice: item.listPrice,
    salePrice: item.salePrice,
    discountRate: item.discountRate,
    badges: [],
    rating: 0,
    reviewCount: 0,
    wished: false,
    todayDreamAvailable: false,
    // 랭킹 API(RankingItem)는 태그를 내려주지 않는다 — 위 나머지 필드와 같은 이유로 중립값(빈 배열).
    tags: [],
  };
}

/**
 * 랭킹 페이지 `/ranking`. 설계 6장 — 카테고리 탭 × 순위.
 * GoodsGrid는 순위 숫자를 얹을 자리가 없어(설계상 카드 재사용은 유지하되) 그대로 쓰지 않고,
 * GoodsCard를 그대로 재사용하면서 순위 배지만 형제 요소로 겹쳐 그리는 전용 그리드를 구성한다.
 */
export function Ranking() {
  useTitle('랭킹');
  const [searchParams, setSearchParams] = useSearchParams();
  const categoryCode = searchParams.get('category') ?? undefined;
  const toggleWish = useWishToggle();

  const rankingQuery = useQuery({
    queryKey: ['ranking', categoryCode],
    queryFn: () => fetchRanking(categoryCode),
  });

  function handleSelectCategory(code?: string) {
    if (code) {
      setSearchParams({ category: code });
    } else {
      setSearchParams({});
    }
  }

  const items = rankingQuery.data ?? [];

  return (
    <div className="bb-ranking">
      <h1 className="bb-ranking__title">랭킹</h1>

      <CategoryTabs selected={categoryCode} onSelect={handleSelectCategory} />

      {rankingQuery.isLoading && (
        <div className="bb-goods-grid">
          {Array.from({ length: 10 }).map((_, index) => (
            <GoodsCardSkeleton key={index} />
          ))}
        </div>
      )}

      {rankingQuery.isError && (
        <ErrorState title="랭킹을 불러오지 못했어요" onRetry={() => rankingQuery.refetch()} />
      )}

      {!rankingQuery.isLoading && !rankingQuery.isError && items.length === 0 && (
        <EmptyState title="아직 랭킹 정보가 없어요" description="다른 카테고리를 확인해 보세요" />
      )}

      {!rankingQuery.isLoading && !rankingQuery.isError && items.length > 0 && (
        <div className="bb-goods-grid">
          {items.map((item) => (
            <div key={item.goodsNo} className="bb-ranking-item">
              {/* 순위 배지: 스크린리더도 그대로 읽을 수 있는 실텍스트("N위"). 장식용 아이콘이
                  아니라 순위 정보 자체이므로 aria-hidden을 걸지 않는다. */}
              <span className="bb-ranking-item__rank">{item.rank}위</span>
              {/* 랭킹 응답에는 wished가 없어 위 변환이 항상 false를 채운다 — 눌린 하트는
                  wishStore 오버레이가 켜준다. 새로고침 전까지의 표시만 이 오버레이가 책임진다. */}
              <GoodsCard item={toGoodsListItem(item)} onWishToggle={toggleWish} />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

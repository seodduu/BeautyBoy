import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import * as wishlistApi from '../../api/wishlist';
import { queryKeys } from '../../api/queryKeys';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';
import { GoodsGrid } from '../../components/goods/GoodsGrid';
import { Skeleton } from '../../components/ui/Skeleton';
import { useWishToggle } from '../../features/wishlist/useWishToggle';
import './MyWishlist.css';

/**
 * 마이페이지 찜 `/mypage/wishlist`. `GoodsCard`의 하트(♥, aria-label="찜 해제")를 그대로 재사용해
 * 목록·상세와 같은 상품 카드 언어를 쓴다.
 *
 * 해제 동작은 다른 화면과 **같은 훅**(`useWishToggle`)을 쓴다. 이 화면만 별도 뮤테이션을 두면
 * 여기서 해제한 결과가 목록·검색 화면의 하트 표시(wishStore 오버레이)에 반영되지 않아,
 * 해제한 상품에 하트가 켜진 채 남는다. 목록에서 카드가 사라지는 것은 훅이 성공·실패와 무관하게
 * 찜 목록 쿼리를 무효화하고 서버 응답을 다시 받기 때문이다 — 실패하면 그 재조회가 카드를
 * 그대로 돌려주므로 낙관적으로 지워지지 않는다.
 */
export function MyWishlist() {
  const navigate = useNavigate();
  const handleRemove = useWishToggle();
  const wishlistQuery = useQuery({ queryKey: queryKeys.wishlist(), queryFn: wishlistApi.fetchWishlist });

  if (wishlistQuery.isLoading) {
    return (
      <div className="bb-my-wishlist">
        <Skeleton ratio="16 / 5" />
      </div>
    );
  }

  if (wishlistQuery.isError) {
    return (
      <div className="bb-my-wishlist">
        <ErrorState title="찜 목록을 불러오지 못했어요" onRetry={() => wishlistQuery.refetch()} />
      </div>
    );
  }

  const items = wishlistQuery.data ?? [];

  if (items.length === 0) {
    return (
      <div className="bb-my-wishlist">
        <EmptyState
          title="찜한 상품이 없어요"
          description="마음에 드는 상품에 하트를 눌러 모아보세요."
          action={{ label: '상품 보러 가기', onClick: () => navigate('/goods') }}
        />
      </div>
    );
  }

  return (
    <div className="bb-my-wishlist">
      <GoodsGrid items={items} onWishToggle={handleRemove} />
    </div>
  );
}

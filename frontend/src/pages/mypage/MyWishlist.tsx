import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as wishlistApi from '../../api/wishlist';
import { queryKeys } from '../../api/queryKeys';
import { EmptyState } from '../../components/common/EmptyState';
import { GoodsGrid } from '../../components/goods/GoodsGrid';
import { Skeleton } from '../../components/ui/Skeleton';
import './MyWishlist.css';

/**
 * 마이페이지 찜 `/mypage/wishlist`. `GoodsCard`의 하트(♥, aria-label="찜 해제")를 그대로 재사용해
 * 목록·상세와 같은 상품 카드 언어를 쓴다. 하트를 끄면 `removeWish` 후 목록을 재조회해
 * 카드가 화면에서 사라진다 — 낙관적 갱신 없이 서버 응답을 신뢰한다.
 *
 * `useMutation`이 아니라 클릭 핸들러에서 `removeWish`를 직접 호출한다 — react-query의
 * mutate()는 mutationFn 실행을 마이크로태스크 뒤로 미뤄, 클릭 직후 동기 단언(스파이 호출 여부)이
 * 그 시점에 아직 호출되지 않은 것으로 보이는 문제가 있었다(스파이 호출 0건으로 확인).
 */
export function MyWishlist() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const wishlistQuery = useQuery({ queryKey: queryKeys.wishlist(), queryFn: wishlistApi.fetchWishlist });

  function handleRemove(goodsNo: number) {
    wishlistApi.removeWish(goodsNo).then(() => {
      queryClient.invalidateQueries({ queryKey: queryKeys.wishlist() });
    });
  }

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
        <p className="bb-my-wishlist__error">찜 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
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

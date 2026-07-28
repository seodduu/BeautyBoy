import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as wishlistApi from '../../api/wishlist';
import { queryKeys } from '../../api/queryKeys';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';
import { GoodsGrid } from '../../components/goods/GoodsGrid';
import { Skeleton } from '../../components/ui/Skeleton';
import { useToast } from '../../components/ui/useToast';
import './MyWishlist.css';

/**
 * 마이페이지 찜 `/mypage/wishlist`. `GoodsCard`의 하트(♥, aria-label="찜 해제")를 그대로 재사용해
 * 목록·상세와 같은 상품 카드 언어를 쓴다. 하트를 끄면 `removeWish` 후 목록을 재조회해
 * 카드가 화면에서 사라진다 — 낙관적 갱신 없이 서버 응답을 신뢰한다. 실패하면 토스트로 알리고
 * 목록은 그대로 둔다(낙관적 제거를 하지 않으므로 실패해도 카드가 남는다).
 */
export function MyWishlist() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const wishlistQuery = useQuery({ queryKey: queryKeys.wishlist(), queryFn: wishlistApi.fetchWishlist });

  const removeWishMutation = useMutation({
    mutationFn: (goodsNo: number) => wishlistApi.removeWish(goodsNo),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.wishlist() });
    },
    onError: () => {
      toast('찜 해제에 실패했어요. 다시 시도해 주세요', { tone: 'danger' });
    },
  });

  function handleRemove(goodsNo: number) {
    removeWishMutation.mutate(goodsNo);
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

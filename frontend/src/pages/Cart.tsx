import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchCartItems, removeCartItem, updateCartQuantity } from '../api/cart';
import { checkCompat } from '../api/compat';
import { CartLine } from '../components/cart/CartLine';
import { CompatBanner } from '../components/compat/CompatBanner';
import { EmptyState } from '../components/common/EmptyState';
import { Button } from '../components/ui/Button';
import { Skeleton } from '../components/ui/Skeleton';
import { formatWon } from '../components/ui/Price';
import { useToast } from '../components/ui/useToast';
import './Cart.css';

/**
 * 장바구니 `/cart` — 설계 6장. 라인 목록(수량 스텝퍼·삭제) + 성분 궁합 경고 배너(설계 8장
 * "적용 지점 ③") + 합계(안내용) + 주문하기.
 *
 * 합계는 서버가 내려준 lineAmount의 합이다 — 단가×수량을 프론트가 다시 곱하지 않는다(project law).
 * 궁합은 CONFLICT여도 주문하기를 막지 않는다 — 조언이지 금지가 아니다(설계 8장).
 */
export function Cart() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const cartQuery = useQuery({ queryKey: ['cart'], queryFn: fetchCartItems });
  const items = useMemo(() => cartQuery.data ?? [], [cartQuery.data]);

  // 담긴 goodsNo 집합이 바뀔 때만 궁합을 재조회한다 — 수량만 바뀌는 흔한 조작에는 다시 부르지 않는다.
  const goodsNos = useMemo(
    () => Array.from(new Set(items.map((item) => item.goodsNo))).sort((a, b) => a - b),
    [items],
  );

  const compatQuery = useQuery({
    queryKey: ['compat', goodsNos.join(',')],
    queryFn: () => checkCompat(goodsNos),
    enabled: goodsNos.length > 0,
  });

  const quantityMutation = useMutation({
    mutationFn: ({ cartItemId, quantity }: { cartItemId: number; quantity: number }) =>
      updateCartQuantity(cartItemId, quantity),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cart'] }),
  });

  const removeMutation = useMutation({
    mutationFn: (cartItemId: number) => removeCartItem(cartItemId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cart'] });
      toast('삭제했어요');
    },
  });

  if (cartQuery.isLoading) {
    return (
      <div className="bb-cart">
        <Skeleton ratio="16 / 5" />
      </div>
    );
  }

  if (cartQuery.isError) {
    return (
      <div className="bb-cart">
        <p className="bb-cart__error">장바구니를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="bb-cart">
        <h1 className="bb-cart__title">장바구니</h1>
        <EmptyState
          title="장바구니가 비어 있어요"
          description="마음에 드는 상품을 담아보세요."
          action={{ label: '상품 보러 가기', onClick: () => navigate('/goods') }}
        />
      </div>
    );
  }

  const total = items.reduce((sum, item) => sum + item.lineAmount, 0);

  return (
    <div className="bb-cart">
      <h1 className="bb-cart__title">장바구니</h1>

      {compatQuery.data && <CompatBanner result={compatQuery.data} />}

      <div className="bb-cart__lines">
        {items.map((item) => (
          <CartLine
            key={item.cartItemId}
            item={item}
            onQuantityChange={(cartItemId, quantity) =>
              quantityMutation.mutate({ cartItemId, quantity })
            }
            onRemove={(cartItemId) => removeMutation.mutate(cartItemId)}
          />
        ))}
      </div>

      <div className="bb-cart__summary">
        <span className="bb-cart__summary-label">결제 예상 금액</span>
        <span className="bb-cart__summary-total" data-testid="cart-total">
          {formatWon(total)}
        </span>
      </div>
      <p className="bb-cart__summary-note">배송비는 주문서에서 계산됩니다</p>

      <Button className="bb-cart__cta" variant="primary" onClick={() => navigate('/order')}>
        주문하기
      </Button>
    </div>
  );
}

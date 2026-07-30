import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchCartItems, removeCartItem, updateCartQuantity, type CartItem } from '../api/cart';
import { checkCompat } from '../api/compat';
import { queryKeys } from '../api/queryKeys';
import { CartLine } from '../components/cart/CartLine';
import { CompatBanner } from '../components/compat/CompatBanner';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorState } from '../components/common/ErrorState';
import { Button } from '../components/ui/Button';
import { Skeleton } from '../components/ui/Skeleton';
import { formatWon } from '../components/ui/Price';
import { useToast } from '../components/ui/useToast';
import { useTitle } from '../hooks/useTitle';
import './Cart.css';

/**
 * 장바구니 `/cart` — 설계 6장. 라인 목록(수량 스텝퍼·삭제) + 성분 궁합 경고 배너(설계 8장
 * "적용 지점 ③") + 합계(안내용) + 주문하기.
 *
 * 합계는 서버가 내려준 lineAmount의 합이다 — 단가×수량을 프론트가 다시 곱하지 않는다(project law).
 * 궁합은 CONFLICT여도 주문하기를 막지 않는다 — 조언이지 금지가 아니다(설계 8장).
 */
export function Cart() {
  useTitle('장바구니');
  const navigate = useNavigate();
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const cartQuery = useQuery({
    queryKey: queryKeys.cart(),
    queryFn: fetchCartItems,
    // 수량·재고·합계가 곧 결제 금액이다 — 전역 60초 staleTime을 여기서 덮어써 항상 최신을 받는다.
    staleTime: 0,
  });
  const items = useMemo(() => cartQuery.data ?? [], [cartQuery.data]);

  // 담긴 goodsNo 집합이 바뀔 때만 궁합을 재조회한다 — 수량만 바뀌는 흔한 조작에는 다시 부르지 않는다.
  const goodsNos = useMemo(
    () => Array.from(new Set(items.map((item) => item.goodsNo))).sort((a, b) => a - b),
    [items],
  );

  const compatQuery = useQuery({
    queryKey: queryKeys.compat(goodsNos),
    queryFn: () => checkCompat(goodsNos),
    enabled: goodsNos.length > 0,
  });

  const quantityMutation = useMutation({
    mutationFn: ({ cartItemId, quantity }: { cartItemId: number; quantity: number }) =>
      updateCartQuantity(cartItemId, quantity),

    // 낙관적 갱신. 서버 왕복 전에 캐시를 직접 고쳐 스테퍼가 즉시 따라오게 한다.
    onMutate: async ({ cartItemId, quantity }) => {
      // 진행 중인 ['cart'] 조회를 먼저 취소한다. 안 하면 내 낙관적 값이 캐시에 들어간 직후
      // 먼저 나가 있던 GET의 (옛) 응답이 도착해 덮어써, 눌렀는데 되돌아가는 것처럼 보인다.
      await queryClient.cancelQueries({ queryKey: queryKeys.cart() });

      const previous = queryClient.getQueryData<CartItem[]>(queryKeys.cart());

      queryClient.setQueryData<CartItem[]>(queryKeys.cart(), (items) =>
        items?.map((item) =>
          item.cartItemId === cartItemId
            ? // 합계도 같이 움직여야 한다. 화면 합계는 lineAmount의 합이므로 여기서
              // lineAmount를 같이 고치지 않으면 수량만 바뀌고 금액이 늦게 따라와 더 어색해진다.
              //
              // 여기서만 단가×수량을 곱한다 — "합계는 서버 lineAmount의 합"이라는 규칙을
              // 어기는 것이 아니라, 서버 값이 도착하기 전 몇백 ms의 예측치를 그리는 것이다.
              // onSettled의 invalidate가 서버 값으로 반드시 덮어쓴다(할인·쿠폰이 붙어
              // 예측이 틀리더라도 최종 화면은 서버가 정한다). 돈의 진실은 여전히 서버다.
              { ...item, quantity, lineAmount: item.unitPrice * quantity }
            : item,
        ),
      );

      return { previous };
    },

    // 롤백 + 실패 안내. 두 가지를 함께 한다 — 롤백은 화면을 되돌릴 뿐
    // 왜 되돌아갔는지는 말해주지 않으므로, 낙관적 갱신에는 토스트가 더 필요하다.
    // (결함 마감 웨이브 §2가 넣은 토스트를 지우지 않고 그대로 살린다.)
    onError: (_error, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(queryKeys.cart(), context.previous);
      }
      toast('수량 변경에 실패했어요. 다시 시도해 주세요', { tone: 'danger' });
    },

    // 성공이든 실패든 서버 값으로 맞춘다. onSuccess가 아니라 onSettled인 이유:
    // 실패 시에도 서버의 진짜 값을 다시 받아야 한다 — 요청이 실제로는 적용됐는데
    // 응답만 못 받은 경우, 롤백한 화면이 서버와 어긋난 채로 남는다.
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.cart() }),
  });

  const removeMutation = useMutation({
    mutationFn: (cartItemId: number) => removeCartItem(cartItemId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.cart() });
      toast('삭제했어요');
    },
    onError: () => toast('삭제하지 못했어요. 다시 시도해 주세요', { tone: 'danger' }),
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
        <ErrorState title="장바구니를 불러오지 못했어요" onRetry={() => cartQuery.refetch()} />
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

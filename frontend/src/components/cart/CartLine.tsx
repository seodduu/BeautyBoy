import type { CartItem } from '../../api/cart';
import { formatWon } from '../ui/Price';
import { QuantityStepper } from '../goods/QuantityStepper';
import './CartLine.css';

interface CartLineProps {
  item: CartItem;
  onQuantityChange: (cartItemId: number, quantity: number) => void;
  onRemove: (cartItemId: number) => void;
}

/**
 * 장바구니에서 늘릴 수 있는 상한. 상세 페이지와 달리 라인에는 남은 재고 정보가
 * 내려오지 않으므로(CartItem 계약), 실 재고 초과는 주문 시점에 서버가 재검증한다(project law).
 */
const MAX_QUANTITY = 99;

/** 장바구니 한 줄 — 상품명/옵션 + 수량 스텝퍼 + 라인 금액(서버 lineAmount 그대로) + 삭제. */
export function CartLine({ item, onQuantityChange, onRemove }: CartLineProps) {
  return (
    <article className="bb-cart-line">
      <div className="bb-cart-line__info">
        <p className="bb-cart-line__name">{item.goodsName}</p>
        {item.optionName && <p className="bb-cart-line__option">{item.optionName}</p>}
      </div>

      <QuantityStepper
        quantity={item.quantity}
        max={MAX_QUANTITY}
        onChange={(next) => onQuantityChange(item.cartItemId, next)}
      />

      <p className="bb-cart-line__amount">{formatWon(item.lineAmount)}</p>

      <button
        type="button"
        className="bb-cart-line__remove"
        onClick={() => onRemove(item.cartItemId)}
      >
        삭제
      </button>
    </article>
  );
}

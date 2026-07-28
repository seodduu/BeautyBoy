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
 * 장바구니에서 늘릴 수 있는 상한(오입력 방지 캡). 실제 상한은 min(재고, 99)로,
 * 스텝퍼는 UX 게이트일 뿐 재고 검증의 진실은 서버다(돈과 재고는 서버).
 */
const MAX_QUANTITY = 99;

/** 장바구니 한 줄 — 썸네일 + 상품명/옵션/개당가 + 수량 스텝퍼 + 라인 금액(서버 lineAmount 그대로) + 삭제. */
export function CartLine({ item, onQuantityChange, onRemove }: CartLineProps) {
  return (
    <article className="bb-cart-line" data-testid={`cart-line-${item.cartItemId}`}>
      {/* alt="" — 상품명 텍스트가 바로 옆에 있어 이미지는 장식이다 */}
      {item.thumbnailUrl ? (
        <img className="bb-cart-line__thumbnail" src={item.thumbnailUrl} alt="" />
      ) : (
        <div className="bb-cart-line__thumbnail" aria-hidden="true" />
      )}

      <div className="bb-cart-line__info">
        <p className="bb-cart-line__name">{item.goodsName}</p>
        {item.optionName && <p className="bb-cart-line__option">{item.optionName}</p>}
        <p className="bb-cart-line__unit-price">개당 {formatWon(item.unitPrice)}</p>
      </div>

      <QuantityStepper
        quantity={item.quantity}
        max={Math.min(item.stock, MAX_QUANTITY)}
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

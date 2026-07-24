import './Price.css';

interface PriceProps {
  listPrice: number;
  salePrice: number;
  discountRate: number;
}

/** 천 단위 구분자 포맷터 — Price가 쓰는 것과 동일한 규칙을 다른 컴포넌트(OptionSelector 등)도 재사용한다. */
export function formatWon(value: number) {
  return `${value.toLocaleString('ko-KR')}원`;
}

/**
 * DESIGN.md price-discount-rate / price-sale / price-list-struck.
 * 순서: 할인율(signal-sale) → 판매가(ink) → 정가 취소선(ash).
 * discountRate===0이면 정가 노드를 아예 렌더하지 않는다.
 */
export function Price({ listPrice, salePrice, discountRate }: PriceProps) {
  return (
    <div className="bb-price">
      {discountRate > 0 && <span className="bb-price__rate">{discountRate}%</span>}
      <span className="bb-price__sale">{formatWon(salePrice)}</span>
      {discountRate > 0 && <span className="bb-price__list">{formatWon(listPrice)}</span>}
    </div>
  );
}

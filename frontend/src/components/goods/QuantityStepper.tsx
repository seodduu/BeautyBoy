import './QuantityStepper.css';

interface QuantityStepperProps {
  quantity: number;
  /** 선택한 옵션의 재고. 옵션 미선택 시 1(더 늘릴 수 없음). */
  max: number;
  onChange: (next: number) => void;
}

/**
 * 상세 페이지 수량 조절 — 하한 1, 상한은 선택 옵션의 stock.
 * 값은 role="status"로 읽혀 스크린리더가 변경을 알 수 있게 한다(DESIGN.md UX 계약 §접근성).
 */
export function QuantityStepper({ quantity, max, onChange }: QuantityStepperProps) {
  const lowerBound = 1;
  const upperBound = Math.max(max, lowerBound);

  return (
    <div className="bb-quantity-stepper">
      <button
        type="button"
        className="bb-quantity-stepper__button"
        aria-label="수량 줄이기"
        disabled={quantity <= lowerBound}
        onClick={() => onChange(Math.max(lowerBound, quantity - 1))}
      >
        −
      </button>
      <span className="bb-quantity-stepper__value" role="status">
        {quantity}
      </span>
      <button
        type="button"
        className="bb-quantity-stepper__button"
        aria-label="수량 늘리기"
        disabled={quantity >= upperBound}
        onClick={() => onChange(Math.min(upperBound, quantity + 1))}
      >
        +
      </button>
    </div>
  );
}

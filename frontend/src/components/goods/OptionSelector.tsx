import type { GoodsOption } from '../../types/detail';
import { formatWon } from '../ui/Price';
import './OptionSelector.css';

interface OptionSelectorProps {
  options: GoodsOption[];
  /** 선택된 옵션 번호. 아직 아무것도 고르지 않았으면 null. */
  selectedOptionNo: number | null;
  onSelect: (optionNo: number) => void;
}

/**
 * 상세 페이지 옵션 선택 UI — role="radiogroup" + role="radio"(네이티브 input[type=radio]).
 * 옵션이 2개 이상일 때만 Detail.tsx가 이 컴포넌트를 그린다(옵션이 하나면 고를 것이 없다).
 * 표시 규칙: addPrice>0이면 "(+3,000원)", 품절(stock===0 또는 soldOut)이면 "(품절)" + 선택 불가.
 */
export function OptionSelector({ options, selectedOptionNo, onSelect }: OptionSelectorProps) {
  return (
    <div className="bb-option-selector" role="radiogroup" aria-label="옵션 선택">
      {options.map((option) => {
        const soldOut = option.soldOut || option.stock === 0;
        const priceSuffix = option.addPrice > 0 ? ` (+${formatWon(option.addPrice)})` : '';
        const soldOutSuffix = soldOut ? ' (품절)' : '';

        return (
          <label
            key={option.optionNo}
            className={`bb-option-selector__item${
              soldOut ? ' bb-option-selector__item--sold-out' : ''
            }`}
          >
            <input
              type="radio"
              className="bb-option-selector__input"
              name="goods-option"
              value={option.optionNo}
              checked={selectedOptionNo === option.optionNo}
              disabled={soldOut}
              onChange={() => {
                // jsdom은 disabled input의 클릭을 브라우저처럼 항상 막지 않으므로 방어적으로 한 번 더 확인한다.
                if (!soldOut) {
                  onSelect(option.optionNo);
                }
              }}
            />
            <span className="bb-option-selector__label">
              {option.name}
              {priceSuffix}
              {soldOutSuffix}
            </span>
          </label>
        );
      })}
    </div>
  );
}

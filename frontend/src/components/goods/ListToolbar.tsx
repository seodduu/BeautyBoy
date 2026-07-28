import { Link } from 'react-router-dom';
import type { GoodsSort } from '../../api/goods';
import { ROUTINE_STEPS } from '../../features/routine/steps';
import './ListToolbar.css';

/** DESIGN.md `list-toolbar` 가격대 3종. URL `?price=` 값이자 서버 min/max 매핑의 키다. */
export type PriceBand = 'UNDER_10K' | 'FROM_10K_TO_30K' | 'OVER_30K';

/** 서버 GoodsSort 5종과 1:1 — 임의로 늘리지 않는다 (DESIGN.md list-toolbar). */
const SORT_OPTIONS: ReadonlyArray<{ value: GoodsSort; label: string }> = [
  { value: 'popular', label: '인기순' },
  { value: 'new', label: '최신순' },
  { value: 'sales', label: '판매량순' },
  { value: 'priceAsc', label: '낮은 가격순' },
  { value: 'discount', label: '높은 할인율순' },
];

const PRICE_BANDS: ReadonlyArray<{ value: PriceBand; label: string }> = [
  { value: 'UNDER_10K', label: '1만원 미만' },
  { value: 'FROM_10K_TO_30K', label: '1~3만원' },
  { value: 'OVER_30K', label: '3만원 이상' },
];

interface ListToolbarProps {
  category: string | null;
  sort: GoodsSort;
  priceBand: PriceBand | null;
  onSortChange: (sort: GoodsSort) => void;
  onPriceBandChange: (band: PriceBand | null) => void;
}

/**
 * 카테고리 목록의 제목 아래·그리드 위 한 줄 (DESIGN.md `list-toolbar`).
 * 좌측 루틴 5단계 탭(해당 카테고리일 때만) / 우측 정렬 셀렉트 / 그 아래 가격대 pill.
 * 탭은 자체 <Link> 내비게이션, 정렬·가격대는 콜백으로 올린다 — URL이 상태의 진실이므로
 * 이 컴포넌트는 상태를 갖지 않는다.
 */
export function ListToolbar({
  category,
  sort,
  priceBand,
  onSortChange,
  onPriceBandChange,
}: ListToolbarProps) {
  // 루틴 5단계 안의 카테고리일 때만 탭을 렌더한다 — 단계 밖(C003 등)에서 탭을 보이면
  // "지금 보는 목록이 루틴의 일부"라는 거짓 맥락을 만든다.
  const inRoutine = ROUTINE_STEPS.some((step) => step.categoryCode === category);

  return (
    <div className="bb-list-toolbar">
      <div className="bb-list-toolbar__row">
        {inRoutine && (
          <nav className="bb-list-toolbar__tabs" aria-label="루틴 단계">
            {ROUTINE_STEPS.map((step) => (
              <Link
                key={step.id}
                to={'/goods?category=' + step.categoryCode}
                className="bb-list-toolbar__tab"
                aria-current={step.categoryCode === category ? 'page' : undefined}
              >
                {step.label}
              </Link>
            ))}
          </nav>
        )}
        <select
          className="bb-list-toolbar__sort"
          aria-label="정렬"
          value={sort}
          onChange={(event) => onSortChange(event.target.value as GoodsSort)}
        >
          {SORT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <div className="bb-list-toolbar__price" role="group" aria-label="가격대">
        {PRICE_BANDS.map((band) => {
          const selected = band.value === priceBand;
          return (
            <button
              key={band.value}
              type="button"
              className="bb-list-toolbar__pill"
              aria-pressed={selected}
              onClick={() => onPriceBandChange(selected ? null : band.value)}
            >
              {/* 색 반전만으로 상태를 알리지 않는다 — ✓는 보강 단서 (DESIGN.md "선택 상태 — 색 반전").
                  접근성 이름은 라벨만 유지하도록 aria-hidden. */}
              {selected && <span aria-hidden="true">✓ </span>}
              {band.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

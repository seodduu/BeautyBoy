import { useRef } from 'react';
import type { KeyboardEvent } from 'react';
import './CategoryTabs.css';

/** 설계 6장 `/ranking` 카테고리 탭 — "전체" + 상위 카테고리 6종. */
export interface CategoryTab {
  /** undefined면 "전체"(카테고리 파라미터 없음)를 의미한다. */
  code?: string;
  label: string;
}

export const RANKING_CATEGORY_TABS: CategoryTab[] = [
  { code: undefined, label: '전체' },
  { code: 'C001', label: '스킨케어' },
  { code: 'C002', label: '클렌징' },
  { code: 'C003', label: '헤어·바디' },
  { code: 'C004', label: '선케어' },
  { code: 'C005', label: '쉐이빙·그루밍' },
  { code: 'C006', label: '메이크업' },
];

interface CategoryTabsProps {
  /** 현재 선택된 categoryCode. "전체"는 undefined. */
  selected?: string;
  onSelect: (code?: string) => void;
}

/**
 * WAI-ARIA Tabs 패턴(DetailTabs와 동일한 roving tabindex + 화살표 키 모델).
 * role=tablist/tab이며 탭패널은 없다 — 선택은 그리드 콘텐츠 자체를 바꾸는 것으로 대신한다.
 */
export function CategoryTabs({ selected, onSelect }: CategoryTabsProps) {
  const tabRefs = useRef<Map<number, HTMLButtonElement>>(new Map());

  function moveFocusTo(index: number) {
    tabRefs.current.get(index)?.focus();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    if (event.key === 'ArrowRight') {
      event.preventDefault();
      const nextIndex = (index + 1) % RANKING_CATEGORY_TABS.length;
      onSelect(RANKING_CATEGORY_TABS[nextIndex].code);
      moveFocusTo(nextIndex);
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      const prevIndex = (index - 1 + RANKING_CATEGORY_TABS.length) % RANKING_CATEGORY_TABS.length;
      onSelect(RANKING_CATEGORY_TABS[prevIndex].code);
      moveFocusTo(prevIndex);
    }
    // Enter/Space는 button의 기본 클릭 동작으로 처리된다.
  }

  return (
    <div className="bb-category-tabs" role="tablist" aria-label="랭킹 카테고리">
      {RANKING_CATEGORY_TABS.map((tab, index) => {
        const isActive = selected === tab.code;
        return (
          <button
            key={tab.label}
            ref={(el) => {
              if (el) tabRefs.current.set(index, el);
            }}
            type="button"
            role="tab"
            aria-selected={isActive}
            tabIndex={isActive ? 0 : -1}
            className={`bb-category-tabs__tab${isActive ? ' bb-category-tabs__tab--active' : ''}`}
            onClick={() => onSelect(tab.code)}
            onKeyDown={(event) => handleKeyDown(event, index)}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}

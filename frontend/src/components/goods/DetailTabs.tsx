import { useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { DescriptionPanel } from './DescriptionPanel';
import { ReviewList } from './ReviewList';
import { QnaList } from './QnaList';
import './DetailTabs.css';

type TabKey = 'summary' | 'review' | 'qna';

const TABS: Array<{ key: TabKey; label: string }> = [
  { key: 'summary', label: '설명' },
  { key: 'review', label: '리뷰' },
  { key: 'qna', label: 'Q&A' },
];

interface DetailTabsProps {
  goodsNo: number;
}

/**
 * 설계 6장 상세 화면 탭 — 설명/리뷰/Q&A.
 * WAI-ARIA Tabs 패턴: role=tablist/tab/tabpanel + roving tabindex + 화살표 키 이동.
 * 세 탭 모두 activeTab일 때만 useQuery(enabled)로 lazy fetch한다.
 */
export function DetailTabs({ goodsNo }: DetailTabsProps) {
  const [active, setActive] = useState<TabKey>('summary');
  const tabRefs = useRef<Map<TabKey, HTMLButtonElement>>(new Map());

  function moveFocusTo(key: TabKey) {
    setActive(key);
    tabRefs.current.get(key)?.focus();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    if (event.key === 'ArrowRight') {
      event.preventDefault();
      moveFocusTo(TABS[(index + 1) % TABS.length].key);
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      moveFocusTo(TABS[(index - 1 + TABS.length) % TABS.length].key);
    }
    // Enter/Space는 button의 기본 클릭 동작으로 처리된다.
  }

  return (
    <div className="bb-detail-tabs">
      <div className="bb-detail-tabs__list" role="tablist" aria-label="상품 상세 탭">
        {TABS.map((tab, index) => (
          <button
            key={tab.key}
            ref={(el) => {
              if (el) tabRefs.current.set(tab.key, el);
            }}
            type="button"
            role="tab"
            id={`bb-tab-${tab.key}`}
            aria-selected={active === tab.key}
            aria-controls={`bb-tabpanel-${tab.key}`}
            tabIndex={active === tab.key ? 0 : -1}
            className={`bb-detail-tabs__tab${
              active === tab.key ? ' bb-detail-tabs__tab--active' : ''
            }`}
            onClick={() => setActive(tab.key)}
            onKeyDown={(event) => handleKeyDown(event, index)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div
        role="tabpanel"
        id="bb-tabpanel-summary"
        aria-labelledby="bb-tab-summary"
        hidden={active !== 'summary'}
        className="bb-detail-tabs__panel"
      >
        <DescriptionPanel goodsNo={goodsNo} active={active === 'summary'} />
      </div>

      <div
        role="tabpanel"
        id="bb-tabpanel-review"
        aria-labelledby="bb-tab-review"
        hidden={active !== 'review'}
        className="bb-detail-tabs__panel"
      >
        <ReviewList goodsNo={goodsNo} active={active === 'review'} />
      </div>

      <div
        role="tabpanel"
        id="bb-tabpanel-qna"
        aria-labelledby="bb-tab-qna"
        hidden={active !== 'qna'}
        className="bb-detail-tabs__panel"
      >
        <QnaList goodsNo={goodsNo} active={active === 'qna'} />
      </div>
    </div>
  );
}

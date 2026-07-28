import { useRef } from 'react';
import type { KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import type { SetConcept } from '../../features/affinity/setConcepts';
import { SET_LETTERS } from '../../features/affinity/setConcepts';
import './SetTabs.css';

interface SetTabsProps {
  concepts: SetConcept[];
  selected: number;
  onSelect: (index: number) => void;
}

/**
 * /main 히어로 아래 "당신을 위한 세트" 탭 3개 (DESIGN.md `set-tabs`).
 *
 * 표시 전용 — 선택 상태는 부모(Main)가 갖는다. `/main`은 `RequireAuth` 뒤라 이 컴포넌트에
 * 도달하는 시점엔 로그인 상태가 항상 보장된다 — 그래서 props만 받는 순수 표시 컴포넌트다.
 * 개인화된 세트인지 여부는 색으로 구분하지 않는다(액센트 금지, 무채색 사다리). 대신 **전부
 * 비개인화**일 때만 아래에 프로필 등록 유도 문구를 낸다 — 하나라도 개인화됐다면 그 자체로
 * "당신을 위한" 세트라 안내가 불필요하다.
 *
 * 방향키 roving은 CategoryTabs(ranking, tabpanel 없는 tablist)와 동일 패턴을 그대로 따른다.
 */
export function SetTabs({ concepts, selected, onSelect }: SetTabsProps) {
  const allFallback = concepts.every((concept) => !concept.personalized);
  const tabRefs = useRef<Map<number, HTMLButtonElement>>(new Map());

  function moveFocusTo(index: number) {
    tabRefs.current.get(index)?.focus();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    if (event.key === 'ArrowRight') {
      event.preventDefault();
      const nextIndex = (index + 1) % concepts.length;
      onSelect(nextIndex);
      moveFocusTo(nextIndex);
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      const prevIndex = (index - 1 + concepts.length) % concepts.length;
      onSelect(prevIndex);
      moveFocusTo(prevIndex);
    }
    // Enter/Space는 button의 기본 클릭 동작으로 처리된다.
  }

  return (
    <div className="bb-set-tabs">
      <p className="bb-set-tabs__eyebrow">PERSONAL SETS</p>
      <h2 className="bb-set-tabs__title">당신을 위한 세트</h2>

      <div className="bb-set-tabs__list" role="tablist" aria-label="추천 세트">
        {concepts.map((concept, index) => {
          const isActive = index === selected;
          return (
            <button
              key={concept.slug}
              ref={(el) => {
                if (el) tabRefs.current.set(index, el);
              }}
              type="button"
              role="tab"
              aria-selected={isActive}
              tabIndex={isActive ? 0 : -1}
              className="bb-set-tabs__tab"
              onClick={() => onSelect(index)}
              onKeyDown={(event) => handleKeyDown(event, index)}
            >
              세트 {SET_LETTERS[index]} · {concept.label}
            </button>
          );
        })}
      </div>

      {allFallback && (
        <p className="bb-set-tabs__fallback">
          프로필을 등록하면 맞춤 세트로 바뀌어요{' '}
          <Link className="bb-set-tabs__fallback-link" to="/mypage/profile">
            프로필 등록하기
          </Link>
        </p>
      )}
    </div>
  );
}

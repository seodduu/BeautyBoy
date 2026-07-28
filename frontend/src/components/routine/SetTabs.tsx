import { Link } from 'react-router-dom';
import type { SetConcept } from '../../features/affinity/setConcepts';
import { useAuthStore } from '../../stores/authStore';
import './SetTabs.css';

const SET_LETTERS = ['A', 'B', 'C'] as const;

interface SetTabsProps {
  concepts: SetConcept[];
  selected: number;
  onSelect: (index: number) => void;
}

/**
 * /main 히어로 아래 "당신을 위한 세트" 탭 3개 (DESIGN.md `set-tabs`).
 *
 * 표시 전용 — 선택 상태는 부모(Main)가 갖는다. 개인화된 세트인지 여부는 색으로 구분하지
 * 않는다(액센트 금지, 무채색 사다리). 대신 **전부 비개인화**일 때만 아래에 프로필 등록
 * 유도 문구를 낸다 — 하나라도 개인화됐다면 그 자체로 "당신을 위한" 세트라 안내가 불필요하다.
 */
export function SetTabs({ concepts, selected, onSelect }: SetTabsProps) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const allFallback = concepts.every((concept) => !concept.personalized);

  return (
    <div className="bb-set-tabs">
      <p className="bb-set-tabs__eyebrow">PERSONAL SETS</p>
      <h2 className="bb-set-tabs__title">당신을 위한 세트</h2>

      <div className="bb-set-tabs__list" role="tablist" aria-label="추천 세트">
        {concepts.map((concept, index) => (
          <button
            key={concept.slug}
            type="button"
            role="tab"
            aria-selected={index === selected}
            className="bb-set-tabs__tab"
            onClick={() => onSelect(index)}
          >
            세트 {SET_LETTERS[index]} · {concept.label}
          </button>
        ))}
      </div>

      {allFallback && (
        <p className="bb-set-tabs__fallback">
          프로필을 등록하면 맞춤 세트로 바뀌어요{' '}
          <Link className="bb-set-tabs__fallback-link" to={accessToken ? '/mypage/profile' : '/signup'}>
            {accessToken ? '프로필 등록하기' : '가입하기'}
          </Link>
        </p>
      )}
    </div>
  );
}

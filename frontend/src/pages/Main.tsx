import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { RoutineSection } from '../components/routine/RoutineSection';
import { ROUTINE_STEPS } from '../features/routine/steps';
import { fetchMe } from '../api/member';
import { readEvents } from '../features/affinity/events';
import { loadFlowRules } from '../features/affinity/flowRules';
import { effectiveConcerns, preferredTextures, tierOf } from '../features/affinity/profile';
import { matchByBehavior, matchByProfile, type Target } from '../features/affinity/match';
import { useAuthStore } from '../stores/authStore';
import type { SkinType } from '../api/routine';
import './Main.css';

/**
 * 루틴 메인 페이지.
 *
 * 핵심 개념: 스크롤을 내리는 순서가 곧 스킨케어 루틴 순서다.
 * 타겟이 "뭘 사야 할지 모르는 남성"이라 순서 자체가 교육이 된다.
 *
 * 5섹션은 스크롤이 길어지므로 sticky 앵커 네비로 현재 위치를 계속 알려주고
 * 원하는 단계로 바로 건너뛸 수 있게 한다.
 *
 * 개인화(설계 §6)는 이 페이지가 계산하고 섹션은 결과만 받는다 — 섹션 다섯이 각자 프로필을 읽으면
 * "최대 2섹션"이라는 전역 상한을 아무도 못 지킨다. 규칙·프로필을 못 받아도 화면은 그대로 뜬다:
 * 개인화가 없는 상태가 곧 기존 화면이라 빈 슬롯이 생기지 않는다.
 */
export function Main() {
  const [activeId, setActiveId] = useState<string>(ROUTINE_STEPS[0].id);
  const accessToken = useAuthStore((state) => state.accessToken);

  // 마운트 시점 스냅샷. localStorage는 이벤트를 발생시키지 않으므로 렌더 중에 다시 읽어봐야
  // 얻을 것이 없고, 스크롤 도중 섹션이 바뀌는 것은 오히려 혼란스럽다(Routine.tsx와 같은 판단).
  const [events] = useState(() => readEvents());

  const meQuery = useQuery({ queryKey: ['me'], queryFn: fetchMe, enabled: !!accessToken });
  const rulesQuery = useQuery({ queryKey: ['flow-rules'], queryFn: loadFlowRules });

  const { targets, textures } = useMemo(() => {
    const rules = rulesQuery.data;
    const profileConcerns = meQuery.data?.concerns ?? [];
    const concerns = effectiveConcerns(
      profileConcerns,
      (meQuery.data?.skinType ?? null) as SkinType | null,
    );
    const textures = preferredTextures(profileConcerns);

    if (!rules) {
      return { targets: [] as Target[], textures };
    }
    const tier = tierOf(events, concerns);
    if (tier === 2) {
      return { targets: matchByBehavior(events, rules.flowRules, concerns), textures };
    }
    if (tier === 1) {
      return { targets: matchByProfile(concerns, rules.concernRules), textures };
    }
    return { targets: [] as Target[], textures };
  }, [events, meQuery.data, rulesQuery.data]);

  useEffect(() => {
    // 화면 상단 1/3 지점을 지나는 섹션을 "현재 단계"로 본다.
    // rootMargin 하단을 크게 깎아, 스크롤을 내릴 때 다음 섹션이 위쪽에 닿는 순간 전환되게 한다.
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setActiveId(entry.target.id);
          }
        }
      },
      { rootMargin: '-30% 0px -60% 0px', threshold: 0 },
    );

    for (const step of ROUTINE_STEPS) {
      const element = document.getElementById(step.id);
      if (element) observer.observe(element);
    }

    return () => observer.disconnect();
  }, []);

  return (
    <div className="bb-main">
      {/* 상단 검정 밴드: 검정 헤더에서 이어지는 반전 히어로. 여기서 밝기가 흰 콘텐츠로 계단식 하강한다.
          풀 히어로가 아니라 밴드라 첫 스크롤 안에 STEP 01이 보인다. */}
      <header className="bb-main__intro">
        <div className="bb-main__intro-inner">
          <p className="bb-main__eyebrow">DAILY ROUTINE</p>
          <h1 className="bb-main__title">순서대로 따라오면 됩니다</h1>
          <p className="bb-main__lede">
            씻고, 정돈하고, 채우고, 덮고, 막는 다섯 단계. 아래로 내리는 순서가 그대로 루틴 순서입니다.
          </p>
        </div>
      </header>

      {/* 흰 콘텐츠 영역. 앵커 네비는 인트로 밖, 이 영역의 직계 자식이어야 한다 —
          인트로(검정 밴드) 안에 두면 sticky 컨테이닝 블록이 밴드가 되어, 밴드가 스크롤로
          사라지는 순간 네비도 함께 사라진다(5섹션 내내 붙어 있어야 하는데). */}
      <div className="bb-main__body">
        <nav className="bb-main__nav" aria-label="루틴 단계 바로가기">
          <ol className="bb-main__nav-list">
            {ROUTINE_STEPS.map((step) => (
              <li key={step.id}>
                <a
                  className={`bb-main__nav-link${
                    activeId === step.id ? ' bb-main__nav-link--active' : ''
                  }`}
                  href={`#${step.id}`}
                  aria-current={activeId === step.id ? 'true' : undefined}
                >
                  <span className="bb-main__nav-order">{String(step.order).padStart(2, '0')}</span>
                  <span className="bb-main__nav-label">{step.label}</span>
                </a>
              </li>
            ))}
          </ol>
        </nav>

        {ROUTINE_STEPS.map((step, index) => {
          const target = targets.find((t) => t.stepId === step.id);
          return (
            <RoutineSection
              key={step.id}
              step={step}
              index={index}
              override={target && { tag: target.tag, reason: target.reason }}
              preferredTextures={textures}
            />
          );
        })}
      </div>
    </div>
  );
}

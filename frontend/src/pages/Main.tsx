import { useEffect, useState } from 'react';
import { RoutineSection } from '../components/routine/RoutineSection';
import { ROUTINE_STEPS } from '../features/routine/steps';
import './Main.css';

/**
 * 루틴 메인 페이지.
 *
 * 핵심 개념: 스크롤을 내리는 순서가 곧 스킨케어 루틴 순서다.
 * 타겟이 "뭘 사야 할지 모르는 남성"이라 순서 자체가 교육이 된다.
 *
 * 5섹션은 스크롤이 길어지므로 sticky 앵커 네비로 현재 위치를 계속 알려주고
 * 원하는 단계로 바로 건너뛸 수 있게 한다.
 */
export function Main() {
  const [activeId, setActiveId] = useState<string>(ROUTINE_STEPS[0].id);

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
      {/* 인트로는 가볍게 — 풀 히어로를 두면 첫 스크롤에서 STEP 01이 안 보인다. */}
      <header className="bb-main__intro">
        <p className="bb-main__eyebrow">DAILY ROUTINE</p>
        <h1 className="bb-main__title">순서대로 따라오면 됩니다</h1>
        <p className="bb-main__lede">
          씻고, 정돈하고, 채우고, 덮고, 막는 다섯 단계. 아래로 내리는 순서가 그대로 루틴 순서입니다.
        </p>
      </header>

      {/* 앵커 네비는 인트로 밖, .bb-main의 직계 자식이어야 한다.
          인트로(header) 안에 두면 sticky의 컨테이닝 블록이 인트로가 되어,
          인트로가 화면 위로 스크롤되는 순간 네비도 함께 사라진다(5섹션 내내 붙어 있어야 하는데). */}
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

      {ROUTINE_STEPS.map((step, index) => (
        <RoutineSection key={step.id} step={step} index={index} />
      ))}
    </div>
  );
}

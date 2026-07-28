import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { RoutineSection } from '../components/routine/RoutineSection';
import { Button } from '../components/ui/Button';
import { useToast } from '../components/ui/useToast';
import { addSetToCart } from '../features/routine/addSetToCart';
import { ROUTINE_STEPS } from '../features/routine/steps';
import { useComposer } from '../features/affinity/useComposer';
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
 * 개인화 v2(루틴 조합기 설계)는 `useComposer`가 계산하고 섹션은 결과만 받는다 — 조합은
 * 체인이라(앞 단계 픽이 다음 단계의 입력이다) 섹션 다섯이 각자 계산할 수 없다. 신호·규칙·궁합을
 * 하나도 못 받아도 화면은 그대로 뜬다: 그 상태가 곧 인기순 기준선이라 빈 슬롯이 생기지 않는다.
 */
export function Main() {
  const [activeId, setActiveId] = useState<string>(ROUTINE_STEPS[0].id);
  const [addingAll, setAddingAll] = useState(false);
  const { toast } = useToast();
  const queryClient = useQueryClient();

  // 세트 컨셉 오버라이드 없이 호출한다 — 프로필 고민 전체로 조합하는 원래 동작(설계 §3).
  const states = useComposer();
  const picks = states
    .map((state) => state.composition?.pick)
    .filter((pick): pick is NonNullable<typeof pick> => !!pick);

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

  /**
   * 루틴 전체 담기(설계 §4.3) — 담기 루프와 집계는 `addSetToCart`에 위임하고,
   * 여기서는 토스트 문구와 로딩 상태만 맡는다. 정책 설명(왜 부분 실패를 롤백하지 않는가)은
   * `addSetToCart` 쪽 주석 참고.
   */
  async function handleAddAll() {
    setAddingAll(true);
    try {
      const { added, skipped } = await addSetToCart(
        queryClient,
        picks.map((pick) => pick.goodsNo),
      );
      if (added === 0) {
        toast('담지 못했어요. 잠시 후 다시 시도해 주세요', { tone: 'danger' });
      } else if (skipped === 0) {
        toast(`${added}개 담았어요`);
      } else {
        toast(`${added}개 담았어요 — ${skipped}개는 품절로 제외`);
      }
    } finally {
      setAddingAll(false);
    }
  }

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
          <Link className="bb-main__sets-link" to="/sets">
            맞춤형 세트 보러가기 →
          </Link>
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

        {ROUTINE_STEPS.map((step, index) => (
          <RoutineSection
            key={step.id}
            step={step}
            index={index}
            composition={states[index].composition}
            pool={states[index].pool}
            isError={states[index].isError}
          />
        ))}

        {/* 루틴 전체 담기 — STEP 05 아래 하나. DESIGN.md {routine-bulk-cta}: 검정 채움을 쓰지 않는다
            (같은 화면의 픽 카드 [바로 담기]가 그 화면의 유일한 검정 알약이어야 한다). */}
        <div className="bb-main__bulk">
          <Button
            className="bb-main__bulk-cta"
            variant="ghost"
            loading={addingAll}
            disabled={picks.length === 0}
            onClick={handleAddAll}
          >
            오늘의 루틴 {picks.length}개 담기
          </Button>
        </div>
      </div>
    </div>
  );
}

import { useEffect, useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { RoutineSection } from '../components/routine/RoutineSection';
import { SetTabs } from '../components/routine/SetTabs';
import { addPickToCart } from '../components/routine/PickCard';
import { Button } from '../components/ui/Button';
import { useToast } from '../components/ui/useToast';
import { ROUTINE_STEPS } from '../features/routine/steps';
import { useComposer } from '../features/affinity/useComposer';
import { deriveSetConcepts } from '../features/affinity/setConcepts';
import { fetchMe } from '../api/member';
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
 * 개인화 v2(루틴 조합기 설계)는 `useComposer`가 계산하고 섹션은 결과만 받는다 — 조합은
 * 체인이라(앞 단계 픽이 다음 단계의 입력이다) 섹션 다섯이 각자 계산할 수 없다. 신호·규칙·궁합을
 * 하나도 못 받아도 화면은 그대로 뜬다: 그 상태가 곧 인기순 기준선이라 빈 슬롯이 생기지 않는다.
 */
export function Main() {
  const [activeId, setActiveId] = useState<string>(ROUTINE_STEPS[0].id);
  const [addingAll, setAddingAll] = useState(false);
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const accessToken = useAuthStore((s) => s.accessToken);
  // useComposer 내부와 같은 ['me'] 키 — 네트워크 왕복은 react-query 캐시가 흡수한다.
  const meQuery = useQuery({ queryKey: ['me'], queryFn: fetchMe, enabled: !!accessToken });
  // me가 확정되기 전에 탭을 그리면 프로필 도착 순간 탭이 갈아엎어진다(스펙 §5).
  //
  // 브리프 원안은 `!accessToken ||`로 비로그인을 즉시 확정 취급했다. 이 화면은 router.tsx의
  // RequireAuth가 지켜 로그인 없이는 도달할 수 없으므로(오케스트레이터 판정 (1)), 실사용에서
  // accessToken이 없는 상태는 없다 — 그 분기는 이 파일 단독 렌더 테스트(비로그인 시뮬레이션)만
  // 통과하게 하는 지름길이었다. 그런데 그 지름길을 넣으면 비로그인 시뮬레이션에서 SetTabs가
  // 즉시 마운트돼 `<h2>당신을 위한 세트</h2>`가 끼어들고, concepts[0](폴백 'pore')이 즉시
  // useComposer로 흘러들어가 콜드스타트 전제(신호 0개 → reason 없음)를 깨— 두 기존 테스트가
  // 무너진다. accessToken 없이는 me 쿼리가 절대 끝나지 않으므로(enabled: false) 지름길을 빼면
  // 이 시뮬레이션은 스켈레톤에 영원히 머문다 — 실사용에서는 애초에 못 만드는 상태라 손해가 없다.
  const meSettled = meQuery.isSuccess || meQuery.isError;
  const concepts = useMemo(
    () =>
      deriveSetConcepts(
        meQuery.data?.concerns ?? [],
        (meQuery.data?.skinType ?? null) as SkinType | null,
      ),
    [meQuery.data],
  );
  const [selectedSet, setSelectedSet] = useState(0);
  // 확정 전에도 항상 호출한다(훅 순서). 확정 전엔 useComposer 내부 signalsReady가
  // 같은 me 쿼리를 기다리므로 조합이 시작되지 않는다 — 픽이 두 번 계산되는 일은 없다.
  // 단, override 인자는 meSettled로 한 번 더 게이트한다: useComposer 내부 signalsReady는
  // `!accessToken ||`로 비로그인에서 즉시 열리므로(그 파일은 읽기 전용이라 못 고친다), 확정 전에도
  // concepts[0](폴백값)을 그대로 넘기면 비로그인 콜드스타트에도 override가 곧장 걸린다.
  const states = useComposer(meSettled ? concepts[selectedSet].slug : null);
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
   * 루틴 전체 담기(설계 §4.3) — 픽을 순차로 담고 **실패는 건너뛰고 집계로 알린다.**
   * 전부 롤백하지 않는 이유: 장바구니는 편집 가능한 중간 상태라, 4개를 담아 두는 편이
   * "하나가 품절이라 아무것도 못 담았다"보다 항상 낫다.
   */
  async function handleAddAll() {
    setAddingAll(true);
    let added = 0;
    try {
      for (const pick of picks) {
        try {
          await addPickToCart(queryClient, pick.goodsNo);
          added += 1;
        } catch {
          // 품절·네트워크 실패 모두 이 픽만 건너뛴다.
        }
      }
      queryClient.invalidateQueries({ queryKey: ['cart'] });

      const skipped = picks.length - added;
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
        </div>
      </header>

      {/* 흰 콘텐츠 영역. 앵커 네비는 인트로 밖, 이 영역의 직계 자식이어야 한다 —
          인트로(검정 밴드) 안에 두면 sticky 컨테이닝 블록이 밴드가 되어, 밴드가 스크롤로
          사라지는 순간 네비도 함께 사라진다(5섹션 내내 붙어 있어야 하는데). */}
      <div className="bb-main__body">
        {meSettled ? (
          <SetTabs concepts={concepts} selected={selectedSet} onSelect={setSelectedSet} />
        ) : (
          <div className="bb-set-tabs__skeleton" aria-hidden>
            <span className="bb-set-tabs__skeleton-pill" />
            <span className="bb-set-tabs__skeleton-pill" />
            <span className="bb-set-tabs__skeleton-pill" />
          </div>
        )}

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

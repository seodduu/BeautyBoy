import { Link } from 'react-router-dom';
import { GoodsGrid } from '../goods/GoodsGrid';
import { PickCard } from './PickCard';
import { ALTERNATIVE_COUNT, type StepComposition } from '../../features/affinity/composer';
import { ROUTINE_SECTION_SIZE, type RoutineStep } from '../../features/routine/steps';
import type { GoodsListItem } from '../../types/goods';
import './RoutineSection.css';

interface RoutineSectionProps {
  step: RoutineStep;
  /** 0-based 순서. 짝수면 타이포가 왼쪽, 홀수면 오른쪽 — 스크롤에 리듬을 준다. */
  index: number;
  /**
   * 이 단계의 조합 결과(설계 §3). `undefined`면 **미확정**이다 — 앞 단계 픽이 아직 안 정해졌거나
   * 풀을 받는 중이라는 뜻이라 스켈레톤을 유지한다(점진 렌더).
   */
  composition?: StepComposition;
  /** 후보 풀(서버 인기순). 픽이 없을 때 기준선 그리드로 폴백하는 데만 쓴다. */
  pool?: GoodsListItem[];
  /** 풀 조회 실패. 이 섹션만 문구를 내고 다른 단계 진행을 막지 않는다. */
  isError?: boolean;
}

/**
 * 루틴 한 단계를 그리는 섹션.
 *
 * 시각 형식은 pangram 레퍼런스의 "거대 타이포 ↔ 면으로 나뉜 카드" 대비를 따른다:
 * 큰 타이포 블록과 이미지 박스(surface + rounded.lg)가 나란히 서고, 아래에 상품이 붙는다.
 *
 * v2에서 이 컴포넌트는 **데이터를 직접 가져오지 않는다.** 조합은 체인이라 단계 혼자서는
 * 성립하지 않기 때문이다(앞 단계 픽이 이 단계의 입력이다) — Main의 `useComposer`가 계산하고
 * 섹션은 결과만 그린다. 부분 채움·사용감 tie-break는 점수 공식으로 흡수돼 사라졌다(설계 §7).
 */
export function RoutineSection({
  step,
  index,
  composition,
  pool = [],
  isError = false,
}: RoutineSectionProps) {
  const orderLabel = String(step.order).padStart(2, '0');
  const sideClass = index % 2 === 0 ? 'bb-routine--text-left' : 'bb-routine--text-right';

  const pick = composition?.pick ?? null;
  // 픽이 없다(풀이 비었거나 게이트로 전원 탈락) → 기준선 그리드로 폴백한다. 화면이 비는 것보다
  // 인기순이라도 보이는 편이 낫다(설계 §3.3의 "메인이 멈추면 안 된다"와 같은 판단).
  const fallbackItems = pool.slice(0, ROUTINE_SECTION_SIZE);

  return (
    <section id={step.id} className={`bb-routine ${sideClass}`} aria-labelledby={`${step.id}-title`}>
      <div className="bb-routine__head">
        <div className="bb-routine__text">
          <p className="bb-routine__step">STEP {orderLabel}</p>
          <h2 id={`${step.id}-title`} className="bb-routine__title">
            {step.label}
          </h2>
          <p className="bb-routine__copy">{step.copy}</p>
        </div>

        <div className="bb-routine__figure">
          {/* 장식이 아니라 단계를 식별하는 이미지라 alt를 비우지 않는다. */}
          <img className="bb-routine__image" src={step.image} alt={`${step.label} 단계 이미지`} />
        </div>
      </div>

      {isError ? (
        <p className="bb-routine__error">상품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
      ) : composition && pick ? (
        <>
          <PickCard pick={pick} reason={composition.reason} matched={composition.matched} />
          {/* 대안은 점수 2~4위. 픽이 빠진 줄이라 데스크톱에서도 3열로 고정한다(DESIGN.md). */}
          <div className="bb-routine__alternatives">
            <GoodsGrid
              items={composition.alternatives}
              skeletonCount={ALTERNATIVE_COUNT}
              categoryCode={step.categoryCode}
            />
          </div>
        </>
      ) : (
        <GoodsGrid
          items={fallbackItems}
          loading={composition === undefined}
          skeletonCount={ROUTINE_SECTION_SIZE}
          categoryCode={step.categoryCode}
        />
      )}

      <p className="bb-routine__more">
        <Link
          className="bb-routine__more-link"
          to={`/goods?category=${encodeURIComponent(step.categoryCode)}`}
        >
          {step.label} 전체 보기
          <span aria-hidden="true"> →</span>
        </Link>
      </p>
    </section>
  );
}

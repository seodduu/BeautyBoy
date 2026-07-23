import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { fetchGoodsList } from '../../api/goods';
import { GoodsGrid } from '../goods/GoodsGrid';
import { ROUTINE_SECTION_SIZE, type RoutineStep } from '../../features/routine/steps';
import './RoutineSection.css';

interface RoutineSectionProps {
  step: RoutineStep;
  /** 0-based 순서. 짝수면 타이포가 왼쪽, 홀수면 오른쪽 — 스크롤에 리듬을 준다. */
  index: number;
}

/**
 * 루틴 한 단계를 그리는 섹션.
 *
 * 시각 형식은 pangram 레퍼런스의 "거대 타이포 ↔ 면으로 나뉜 카드" 대비를 따른다:
 * 큰 타이포 블록과 이미지 박스(surface + rounded.lg)가 나란히 서고, 아래에 상품 4개가 붙는다.
 * 섹션이 자기 데이터를 직접 가져오므로(부모가 5개를 모아 내려주지 않는다) 단계를 추가·제거해도
 * Main은 상수 배열만 map하면 된다.
 */
export function RoutineSection({ step, index }: RoutineSectionProps) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['routine-goods', step.categoryCode],
    queryFn: () =>
      fetchGoodsList({ page: 0, size: ROUTINE_SECTION_SIZE, categoryCode: step.categoryCode }),
  });

  const orderLabel = String(step.order).padStart(2, '0');
  const sideClass = index % 2 === 0 ? 'bb-routine--text-left' : 'bb-routine--text-right';

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
      ) : (
        <GoodsGrid
          items={data?.content ?? []}
          loading={isLoading}
          skeletonCount={ROUTINE_SECTION_SIZE}
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

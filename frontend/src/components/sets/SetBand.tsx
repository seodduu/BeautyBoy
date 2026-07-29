import type { SetConcept } from '../../features/affinity/setConcepts';
import { ROUTINE_STEPS } from '../../features/routine/steps';
import type { GoodsListItem } from '../../types/goods';
import { Button } from '../ui/Button';
import { Skeleton } from '../ui/Skeleton';
import { GoodsCard } from '../goods/GoodsCard';
import { GoodsCardSkeleton } from '../goods/GoodsCardSkeleton';
import { useWishToggle } from '../../features/wishlist/useWishToggle';
import './SetBand.css';

interface SetBandProps {
  concept: SetConcept;
  /** 'A' | 'B' | 'C' — setConcepts.ts의 SET_LETTERS에서 온다 */
  letter: string;
  /** 단계 순서대로 5칸. 픽이 없는 단계는 null (그 칸만 비고 밴드는 유지) */
  picks: (GoodsListItem | null)[];
  /** 조합이 아직 확정되지 않았다 — 스켈레톤 렌더 */
  loading: boolean;
  onAddSet: () => void;
  adding: boolean;
}

/**
 * DESIGN.md `sets-page` "세트 밴드" 사양의 구현.
 *
 * 표시 전용 컴포넌트 — 내부 상태 없음. 담기 실행·로딩 판정은 부모(Task 4의 /sets 페이지) 소관이다.
 *
 * `picks`는 항상 `ROUTINE_STEPS.length`(5)와 같은 길이여야 한다 — 렌더 계약이다. 부모가 조합
 * 결과를 5단계 순서로 정확히 채워 넘겨야 "01 클렌징" … "05 선크림"이 항상 맞게 그려진다.
 */
export function SetBand({ concept, letter, picks, loading, onAddSet, adding }: SetBandProps) {
  const pickCount = picks.filter((pick): pick is GoodsListItem => pick !== null).length;
  const toggleWish = useWishToggle();

  return (
    <section className="bb-set-band">
      <div className="bb-set-band__header">
        {loading ? (
          <>
            <Skeleton ratio="auto" className="bb-set-band__title-skeleton" />
            <Skeleton ratio="auto" className="bb-set-band__action-skeleton" />
          </>
        ) : (
          <>
            <h2 className="bb-set-band__title">
              세트 {letter} · {concept.label}
            </h2>
            <Button
              className="bb-set-band__action"
              variant="ghost"
              loading={adding}
              disabled={pickCount === 0}
              onClick={onAddSet}
            >
              이 세트 {pickCount}개 담기
            </Button>
          </>
        )}
      </div>

      <ol className="bb-set-band__grid">
        {ROUTINE_STEPS.map((step, index) => {
          const pick = loading ? null : (picks[index] ?? null);
          const orderLabel = String(step.order).padStart(2, '0');

          return (
            <li key={step.id} className="bb-set-band__cell">
              {loading ? (
                <>
                  <Skeleton ratio="auto" className="bb-set-band__label-skeleton" />
                  <GoodsCardSkeleton />
                </>
              ) : (
                <>
                  <p className="bb-set-band__step-label">
                    {orderLabel} {step.label}
                  </p>
                  {pick ? (
                    <GoodsCard
                      item={pick}
                      onWishToggle={toggleWish}
                      categoryCode={step.categoryCode}
                    />
                  ) : (
                    <p className="bb-set-band__empty">추천할 상품이 없어요</p>
                  )}
                </>
              )}
            </li>
          );
        })}
      </ol>
    </section>
  );
}

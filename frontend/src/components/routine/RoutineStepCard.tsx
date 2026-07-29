import type { RoutineStepResponse } from '../../api/routine';
import { GoodsCard } from '../goods/GoodsCard';
import { useWishToggle } from '../../features/wishlist/useWishToggle';
import './RoutineStepCard.css';

interface RoutineStepCardProps {
  step: RoutineStepResponse;
  /** 이 단계에서 현재 선택된 상품. null이면 아직 아무것도 안 고른 상태(부모가 기본 선택을 채운다). */
  selectedGoodsNo: number | null;
  onSelect: (goodsNo: number) => void;
}

/**
 * 루틴 단계 카드 — 설계 8장 "순서+초보자 설명+추천 2~3개, 기본 선택".
 * 추천 상품은 role="radiogroup"(단계당 하나)로 묶어 한 번에 하나만 고를 수 있게 한다.
 * 라디오를 GoodsCard를 감싸는 <label>에 넣지 않은 이유: GoodsCard 내부에 이미 상세 페이지
 * <Link>가 있어, label로 감싸면 링크 클릭이 라디오 토글과 뒤섞인다(중첩 인터랙티브 요소).
 * 그래서 라디오와 카드를 형제로 두고 라디오의 접근성 이름만 상품명으로 준다.
 */
export function RoutineStepCard({ step, selectedGoodsNo, onSelect }: RoutineStepCardProps) {
  // 루틴은 비회원도 볼 수 있는 화면이다 — 비회원이 하트를 누르면 훅이 로그인으로 안내한다.
  const toggleWish = useWishToggle();

  return (
    <section className="bb-routine-step" data-testid="routine-step">
      <header className="bb-routine-step__header">
        <span className="bb-routine-step__order">STEP {String(step.stepOrder).padStart(2, '0')}</span>
        <h2 className="bb-routine-step__name">{step.stepName}</h2>
        <p className="bb-routine-step__tip">{step.beginnerTip}</p>
      </header>

      <div
        className="bb-routine-step__options"
        role="radiogroup"
        aria-label={`${step.stepName} 추천 상품`}
      >
        {step.recommendations.map((item) => (
          <div key={item.goodsNo} className="bb-routine-step__option">
            <input
              type="radio"
              className="bb-routine-step__input"
              name={`routine-step-${step.stepOrder}`}
              aria-label={item.name}
              checked={selectedGoodsNo === item.goodsNo}
              onChange={() => onSelect(item.goodsNo)}
            />
            <div className="bb-routine-step__card" data-testid="goods-card">
              <GoodsCard item={item} onWishToggle={toggleWish} />
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

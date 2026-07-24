import type { IngredientBadge } from '../../types/detail';
import './IngredientBadges.css';

interface IngredientBadgesProps {
  ingredients: IngredientBadge[];
}

type Tone = 'neutral' | 'caution' | 'danger';

/**
 * DESIGN.md signal-caution/signal-danger — 배경 채움 금지, 글자·아이콘·테두리로만.
 * irritationLevel/comedogenic 중 더 높은 쪽 기준으로 톤을 정한다(둘 다 0~5 스케일).
 * 4 이상은 danger(signal-danger), 3은 caution(signal-caution), 그 이하는 중립(graphite).
 */
function toneFor(irritationLevel: number, comedogenic: number): Tone {
  const max = Math.max(irritationLevel, comedogenic);
  if (max >= 4) return 'danger';
  if (max >= 3) return 'caution';
  return 'neutral';
}

/**
 * 성분 배지 — GET /goods/:goodsNo/ingredients 결과를 그대로 나열한다.
 * ingredients가 비어 있으면 아무것도 그리지 않는다(섹션 자체를 감춘다).
 */
export function IngredientBadges({ ingredients }: IngredientBadgesProps) {
  if (ingredients.length === 0) {
    return null;
  }

  return (
    <ul className="bb-ingredient-badges">
      {ingredients.map((ingredient) => {
        const tone = toneFor(ingredient.irritationLevel, ingredient.comedogenic);

        return (
          <li
            key={ingredient.ingredientId}
            className={`bb-ingredient-badge bb-ingredient-badge--${tone}`}
            title={ingredient.summary}
          >
            <span className="bb-ingredient-badge__name">{ingredient.name}</span>
            <span className="bb-ingredient-badge__level">자극도 {ingredient.irritationLevel}</span>
            {ingredient.key && <span className="bb-ingredient-badge__key">핵심 성분</span>}
          </li>
        );
      })}
    </ul>
  );
}

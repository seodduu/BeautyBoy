import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { IngredientBadges } from './IngredientBadges';
import type { IngredientBadge } from '../../types/detail';

function makeIngredient(overrides: Partial<IngredientBadge> = {}): IngredientBadge {
  return {
    ingredientId: 1,
    name: '나이아신아마이드',
    category: '미백',
    irritationLevel: 1,
    comedogenic: 1,
    summary: '미백 기능성 성분',
    key: false,
    ...overrides,
  };
}

describe('IngredientBadges', () => {
  it('irritationLevel이 높은 성분은 caution/danger 톤 배지로 렌더한다', () => {
    render(<IngredientBadges ingredients={[makeIngredient({ irritationLevel: 5 })]} />);

    const badgeText = screen.getByText(/자극도 5/);
    expect(badgeText.closest('.bb-ingredient-badge')).toHaveClass('bb-ingredient-badge--danger');
  });

  it('irritationLevel이 낮으면 중립 톤 배지로 렌더한다', () => {
    render(<IngredientBadges ingredients={[makeIngredient({ irritationLevel: 1, comedogenic: 1 })]} />);

    const badgeText = screen.getByText(/자극도 1/);
    expect(badgeText.closest('.bb-ingredient-badge')).toHaveClass('bb-ingredient-badge--neutral');
  });

  it('ingredients가 빈 배열이면 아무것도 렌더하지 않는다', () => {
    const { container } = render(<IngredientBadges ingredients={[]} />);
    expect(container).toBeEmptyDOMElement();
  });
});

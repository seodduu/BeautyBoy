import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AssessmentCard } from './AssessmentCard';
import type { GoodsAssessment, FlaggedIngredient } from '../../types/assessment';

const allergen: FlaggedIngredient = {
  ingredientId: 19, name: '리모넨', inciName: 'limonene',
  flags: ['ALLERGEN'], axis: 'CHECK', sourceRef: '착향제 25종',
};
const limitInfo: FlaggedIngredient = {
  ingredientId: 28, name: '토코페롤', inciName: 'tocopherol',
  flags: ['LIMIT'], axis: 'INFO', sourceRef: '* 배합한도 : ...',
};

function base(over: Partial<GoodsAssessment> = {}): GoodsAssessment {
  return {
    goodsNo: 1, verdictCode: 'MOSTLY_FINE', verdictText: '대체로 무난해요',
    checkCount: 1, rinseOff: false, flagged: [allergen], ...over,
  };
}

describe('AssessmentCard', () => {
  it('판정 문구를 보여주고 확인 성분 개수 버튼을 낸다', () => {
    const onOpen = vi.fn();
    render(<AssessmentCard assessment={base()} onOpenPanel={onOpen} />);
    expect(screen.getByText('대체로 무난해요')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /확인 성분 1개/ }));
    expect(onOpen).toHaveBeenCalled();
  });

  it('CHECK 축 성분이 없으면(한도만) 확인 버튼을 숨기고 참고 문구를 낸다', () => {
    render(<AssessmentCard assessment={base({ flagged: [limitInfo] })} onOpenPanel={vi.fn()} />);
    expect(screen.queryByRole('button', { name: /확인 성분/ })).toBeNull();
    expect(screen.getByText(/배합한도가 있는 성분 1개/)).toBeInTheDocument();
  });

  it('숫자 점수(자극도·N점)를 노출하지 않는다', () => {
    const { container } = render(<AssessmentCard assessment={base()} onOpenPanel={vi.fn()} />);
    expect(container.textContent).not.toMatch(/자극도|[0-9]점/);
  });
});

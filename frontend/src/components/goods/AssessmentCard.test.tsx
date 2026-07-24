import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AssessmentCard } from './AssessmentCard';
import type { GoodsAssessment, FlaggedIngredient } from '../../types/assessment';

const allergen: FlaggedIngredient = {
  ingredientId: 19, name: '리모넨', inciName: 'limonene', summary: '시트러스향 향료',
  flags: ['ALLERGEN'], axis: 'CHECK', acidClass: null, limitText: null,
};
const limitInfo: FlaggedIngredient = {
  ingredientId: 28, name: '토코페롤', inciName: 'tocopherol', summary: '비타민E',
  flags: ['LIMIT'], axis: 'INFO', acidClass: null, limitText: '* 배합한도 : ...',
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
    fireEvent.click(screen.getByRole('button', { name: /확인이 필요한 성분 1개/ }));
    expect(onOpen).toHaveBeenCalled();
  });

  it('CHECK 축 성분이 없으면(한도만) 확인 버튼을 숨기고 참고 문구를 낸다', () => {
    render(<AssessmentCard assessment={base({ flagged: [limitInfo] })} onOpenPanel={vi.fn()} />);
    expect(screen.queryByRole('button', { name: /확인이 필요한 성분/ })).toBeNull();
    expect(screen.getByText(/배합한도가 있는 성분 1개/)).toBeInTheDocument();
  });

  it('숫자 점수(자극도·N점)를 노출하지 않는다', () => {
    const { container } = render(<AssessmentCard assessment={base()} onOpenPanel={vi.fn()} />);
    expect(container.textContent).not.toMatch(/자극도|[0-9]점/);
  });

  it('판정 단계별로 신호등 톤 클래스를 준다(초록/주황/빨강)', () => {
    const cases = [
      ['NO_CONCERN', 'success'], ['MOSTLY_FINE', 'success'],
      ['CHECK_SENSITIVE', 'caution'], ['CAUTION', 'danger'], ['REVIEW', 'neutral'],
    ] as const;
    for (const [code, tone] of cases) {
      const { container, unmount } = render(
        <AssessmentCard assessment={base({ verdictCode: code, flagged: [] })} onOpenPanel={vi.fn()} />,
      );
      expect(container.querySelector('.bb-assessment')).toHaveClass(`bb-assessment--${tone}`);
      unmount();
    }
  });
});

import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { CautionPanel } from './CautionPanel';
import type { FlaggedIngredient } from '../../types/assessment';

const limonene: FlaggedIngredient = {
  ingredientId: 19, name: '리모넨', inciName: 'limonene',
  flags: ['ALLERGEN'], axis: 'CHECK', sourceRef: '식약처 착향제 알레르기 유발물질 25종',
};

describe('CautionPanel', () => {
  it('열리면 확인 성분과 근거를 보여준다', () => {
    render(<CautionPanel open flagged={[limonene]} onClose={vi.fn()} />);
    expect(screen.getByText('리모넨')).toBeInTheDocument();
    expect(screen.getByText(/착향제 알레르기 유발물질 25종/)).toBeInTheDocument();
  });

  it('닫혀 있으면 아무것도 렌더하지 않는다', () => {
    const { container } = render(<CautionPanel open={false} flagged={[limonene]} onClose={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('Esc와 닫기 버튼으로 닫힌다', () => {
    const onClose = vi.fn();
    render(<CautionPanel open flagged={[limonene]} onClose={onClose} />);
    fireEvent.keyDown(document, { key: 'Escape' });
    fireEvent.click(screen.getByRole('button', { name: '닫기' }));
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it('오버레이 클릭으로 닫히고, 패널 내부 클릭은 닫지 않는다', () => {
    const onClose = vi.fn();
    render(<CautionPanel open flagged={[limonene]} onClose={onClose} />);
    fireEvent.click(screen.getByRole('dialog')); // 내부 → 전파 차단
    expect(onClose).not.toHaveBeenCalled();
  });

  it('role=dialog + aria-modal', () => {
    render(<CautionPanel open flagged={[limonene]} onClose={vi.fn()} />);
    expect(screen.getByRole('dialog')).toHaveAttribute('aria-modal', 'true');
  });
});

import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { CautionPanel } from './CautionPanel';
import type { FlaggedIngredient } from '../../types/assessment';

const limonene: FlaggedIngredient = {
  ingredientId: 19, name: '리모넨', inciName: 'limonene', summary: '시트러스향에 흔한 향료 성분',
  flags: ['ALLERGEN'], axis: 'CHECK', acidClass: null, limitText: null,
};
const glycolic: FlaggedIngredient = {
  ingredientId: 3, name: '글리콜릭애씨드', inciName: 'glycolic acid', summary: '각질 정돈 대표 AHA, 자극 가능',
  flags: ['EXFOLIANT_ACID'], axis: 'CHECK', acidClass: 'AHA', limitText: null,
};

describe('CautionPanel', () => {
  it('열리면 확인 성분과 근거를 보여준다', () => {
    render(<CautionPanel open flagged={[limonene]} onClose={vi.fn()} />);
    expect(screen.getByText('리모넨')).toBeInTheDocument();
    expect(screen.getByText(/착향제 알레르기 유발물질 25종/)).toBeInTheDocument();
  });

  it('플래그 배지·성분별 설명·분류를 보여주고, 분류는 근거와 분리한다', () => {
    render(<CautionPanel open flagged={[glycolic]} onClose={vi.fn()} />);
    expect(screen.getByText('각질산')).toBeInTheDocument();               // 배지(3.2)
    expect(screen.getByText('각질 정돈 대표 AHA, 자극 가능')).toBeInTheDocument(); // 성분별 설명(3.4)
    expect(screen.getByText('분류: AHA 계열')).toBeInTheDocument();        // 분류(3.3)
    expect(screen.getByText('자체 각질산 분류 기준 v1.0')).toBeInTheDocument(); // 평가 근거(3.3)
  });

  it('열리면 배경 스크롤을 잠그고 닫히면 되돌린다(6.4)', () => {
    const { rerender } = render(<CautionPanel open flagged={[limonene]} onClose={vi.fn()} />);
    expect(document.body.style.overflow).toBe('hidden');
    rerender(<CautionPanel open={false} flagged={[limonene]} onClose={vi.fn()} />);
    expect(document.body.style.overflow).not.toBe('hidden');
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

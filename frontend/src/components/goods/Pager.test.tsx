import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { Pager } from './Pager';

// 계획서 원문은 @testing-library/user-event를 쓰지만 이 프로젝트는 미설치 환경이라
// (ListToolbar.test.tsx 관례) fireEvent로 같은 상호작용을 낸다. 단언은 계획서 그대로.
describe('Pager', () => {
  it('totalPages가 1이면 아무것도 렌더하지 않는다', () => {
    const { container } = render(<Pager page={1} totalPages={1} onPageChange={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('현재 페이지에 aria-current를 달고, 번호 클릭이 1-based로 전달된다', () => {
    const onPageChange = vi.fn();
    render(<Pager page={2} totalPages={3} onPageChange={onPageChange} />);
    expect(screen.getByRole('button', { name: '2' })).toHaveAttribute('aria-current', 'page');
    fireEvent.click(screen.getByRole('button', { name: '3' }));
    expect(onPageChange).toHaveBeenCalledWith(3);
  });

  it('첫 페이지에서 이전이, 끝 페이지에서 다음이 비활성이다', () => {
    const { unmount } = render(<Pager page={1} totalPages={3} onPageChange={vi.fn()} />);
    expect(screen.getByRole('button', { name: '이전' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '다음' })).toBeEnabled();

    unmount();
    render(<Pager page={3} totalPages={3} onPageChange={vi.fn()} />);
    expect(screen.getByRole('button', { name: '이전' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
  });

  it('페이지가 많으면 현재 ±2 윈도로 최대 5개 번호만 보인다', () => {
    render(<Pager page={7} totalPages={20} onPageChange={vi.fn()} />);
    const numbers = screen
      .getAllByRole('button')
      .map((b) => b.textContent)
      .filter((t) => /^\d+$/.test(t ?? ''));
    expect(numbers).toEqual(['5', '6', '7', '8', '9']);
  });

  it('윈도가 끝에 닿으면 안쪽으로 밀어 5개를 유지한다 — 마지막 페이지에서 번호 하나만 남지 않게', () => {
    render(<Pager page={20} totalPages={20} onPageChange={vi.fn()} />);
    const numbers = screen
      .getAllByRole('button')
      .map((b) => b.textContent)
      .filter((t) => /^\d+$/.test(t ?? ''));
    expect(numbers).toEqual(['16', '17', '18', '19', '20']);
  });

  it('이전·다음은 인접 페이지를 1-based로 올린다', () => {
    const onPageChange = vi.fn();
    render(<Pager page={3} totalPages={5} onPageChange={onPageChange} />);
    fireEvent.click(screen.getByRole('button', { name: '이전' }));
    expect(onPageChange).toHaveBeenCalledWith(2);
    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(onPageChange).toHaveBeenCalledWith(4);
  });

  it('페이지 이동 랜드마크로 읽힌다', () => {
    render(<Pager page={1} totalPages={3} onPageChange={vi.fn()} />);
    expect(screen.getByRole('navigation', { name: '페이지 이동' })).toBeInTheDocument();
  });
});

import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { useTitle } from './useTitle';

function Harness({ title }: { title: string | null | undefined }) {
  useTitle(title);
  return null;
}

describe('useTitle', () => {
  it('제목을_설정하면_접미사가_붙는다', () => {
    render(<Harness title="장바구니" />);

    expect(document.title).toBe('장바구니 | 뷰티보이');
  });

  it('null이면_기존_제목을_건드리지_않는다', () => {
    document.title = '이전 제목';

    render(<Harness title={null} />);

    expect(document.title).toBe('이전 제목');
  });

  it('언마운트해도_제목을_되돌리지_않는다', () => {
    const { unmount } = render(<Harness title="장바구니" />);

    unmount();

    expect(document.title).toBe('장바구니 | 뷰티보이');
  });

  it('제목이_바뀌면_다시_설정한다', () => {
    const { rerender } = render(<Harness title="상품" />);
    expect(document.title).toBe('상품 | 뷰티보이');

    rerender(<Harness title="세럼" />);

    expect(document.title).toBe('세럼 | 뷰티보이');
  });
});

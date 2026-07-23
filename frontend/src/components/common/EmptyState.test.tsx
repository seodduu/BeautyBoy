import { expect, test } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EmptyState } from './EmptyState';

test('제목과 설명을 보여준다', () => {
  render(<EmptyState title="표시할 상품이 없어요" description="다른 조건으로 찾아보세요" />);
  expect(screen.getByText('표시할 상품이 없어요')).toBeInTheDocument();
  expect(screen.getByText('다른 조건으로 찾아보세요')).toBeInTheDocument();
});

test('action이 있으면 버튼을 렌더한다', () => {
  render(<EmptyState title="비었어요" action={{ label: '상품 둘러보기', onClick: () => {} }} />);
  expect(screen.getByRole('button', { name: '상품 둘러보기' })).toBeInTheDocument();
});

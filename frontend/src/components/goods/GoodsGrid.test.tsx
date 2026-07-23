import { render, screen } from '@testing-library/react';
import { GoodsGrid } from './GoodsGrid';

test('빈 배열이고 로딩 아니면 빈 상태를 보여준다', () => {
  render(<GoodsGrid items={[]} loading={false} />);
  expect(screen.getByText('표시할 상품이 없어요')).toBeInTheDocument();
});

test('로딩 중이면 빈 상태 대신 스켈레톤을 보여준다', () => {
  render(<GoodsGrid items={[]} loading />);
  expect(screen.queryByText('표시할 상품이 없어요')).toBeNull();
});

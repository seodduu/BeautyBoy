import { expect, test, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ErrorState } from './ErrorState';

test('제목을 보여준다', () => {
  render(<ErrorState title="상품을 불러오지 못했어요" />);
  expect(screen.getByText('상품을 불러오지 못했어요')).toBeInTheDocument();
});

test('설명이 없으면 기본 문구를 쓴다 — 16곳이 같은 말을 복붙하지 않게', () => {
  render(<ErrorState title="상품을 불러오지 못했어요" />);
  expect(screen.getByText('잠시 후 다시 시도해 주세요')).toBeInTheDocument();
});

test('onRetry를 주면 "다시 시도" 버튼이 뜨고 누르면 호출된다', () => {
  const onRetry = vi.fn();
  render(<ErrorState title="상품을 불러오지 못했어요" onRetry={onRetry} />);

  const button = screen.getByRole('button', { name: '다시 시도' });
  fireEvent.click(button);

  expect(onRetry).toHaveBeenCalledTimes(1);
});

test('onRetry가 없으면 버튼을 렌더하지 않는다', () => {
  render(<ErrorState title="상품을 불러오지 못했어요" />);
  expect(screen.queryByRole('button')).not.toBeInTheDocument();
});

test('role="alert"로 읽힌다 — 스크린리더가 오류를 알린다', () => {
  render(<ErrorState title="상품을 불러오지 못했어요" />);
  expect(screen.getByRole('alert')).toBeInTheDocument();
});

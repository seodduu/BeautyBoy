import { expect, test } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Layout } from './Layout';

function renderLayout() {
  render(
    <MemoryRouter>
      <Layout />
    </MemoryRouter>,
  );
}

test('skip-link가 본문 앵커를 가리킨다', () => {
  renderLayout();
  const link = screen.getByRole('link', { name: '본문 바로가기' });
  expect(link).toHaveAttribute('href', '#main-content');
});

test('main 랜드마크에 앵커 id가 있다', () => {
  renderLayout();
  expect(screen.getByRole('main')).toHaveAttribute('id', 'main-content');
});

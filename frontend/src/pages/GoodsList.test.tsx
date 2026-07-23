import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { GoodsList } from './GoodsList';

function renderList(search: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/goods${search}`]}>
        <GoodsList />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('GoodsList — 카테고리 목록', () => {
  it('루틴 단계 코드면 그 단계 이름을 제목으로 쓴다', async () => {
    renderList('?category=C002');

    expect(await screen.findByRole('heading', { name: '클렌징', level: 1 })).toBeInTheDocument();
  });

  it('category 쿼리로 필터한 결과 전체를 보여준다', async () => {
    renderList('?category=C002');

    // C002는 fixture에 8건(C002001:3 + C002002:3 + C002003:2)
    await waitFor(() => {
      expect(screen.getAllByRole('link', { name: /No\./ })).toHaveLength(8);
    });
  });

  it('category가 없으면 전체 상품을 보여준다', async () => {
    renderList('');

    expect(await screen.findByRole('heading', { name: '전체 상품', level: 1 })).toBeInTheDocument();
  });

  it('결과가 0건이면 빈 상태를 보여준다', async () => {
    renderList('?category=C999');

    expect(await screen.findByText('표시할 상품이 없어요')).toBeInTheDocument();
  });
});

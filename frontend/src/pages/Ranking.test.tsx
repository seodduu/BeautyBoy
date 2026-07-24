import { beforeEach, expect, test, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import type { RankingItem } from '../types/ranking';
import { Ranking } from './Ranking';

function makeItem(rank: number, categoryLabel: string): RankingItem {
  return {
    rank,
    goodsNo: rank,
    brandName: `${categoryLabel}브랜드`,
    name: `${categoryLabel} 상품 ${rank}`,
    thumbnailUrl: 'data:image/svg+xml;utf8,<svg/>',
    listPrice: 20000,
    salePrice: 16000,
    discountRate: 20,
    score: 100 - rank,
  };
}

function mockMatchMedia() {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

function renderRanking(initialEntry: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Ranking />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

let lastCategoryCode: string | null | undefined;

beforeEach(() => {
  mockMatchMedia();
  lastCategoryCode = undefined;
  server.use(
    http.get('/api/v1/rankings', ({ request }) => {
      const url = new URL(request.url);
      const categoryCode = url.searchParams.get('categoryCode');
      lastCategoryCode = categoryCode;
      const label = categoryCode ?? '전체';
      const data =
        categoryCode === 'EMPTY'
          ? []
          : [makeItem(1, label), makeItem(2, label), makeItem(3, label)];
      return HttpResponse.json({ code: 'OK', message: '성공', data });
    }),
  );
});

test('전체 탭이 기본 선택되고 카테고리 전체(파라미터 없음)로 랭킹을 조회한다', async () => {
  renderRanking('/ranking');

  const allTab = await screen.findByRole('tab', { name: '전체' });
  expect(allTab).toHaveAttribute('aria-selected', 'true');

  await waitFor(() => expect(lastCategoryCode).toBeNull());
  expect(await screen.findByText('전체 상품 1')).toBeInTheDocument();
});

test('카테고리 탭을 선택하면 categoryCode로 랭킹을 다시 조회하고 ?category=가 갱신된다', async () => {
  renderRanking('/ranking');

  await screen.findByText('전체 상품 1');

  fireEvent.click(screen.getByRole('tab', { name: '스킨케어' }));

  await waitFor(() => expect(lastCategoryCode).toBe('C001'));
  expect(await screen.findByText('C001 상품 1')).toBeInTheDocument();
  expect(screen.getByRole('tab', { name: '스킨케어' })).toHaveAttribute('aria-selected', 'true');
});

test('딥링크(?category=C002)로 들어오면 해당 탭이 선택되고 그 카테고리로 조회한다', async () => {
  renderRanking('/ranking?category=C002');

  const tab = await screen.findByRole('tab', { name: '클렌징' });
  await waitFor(() => expect(tab).toHaveAttribute('aria-selected', 'true'));
  await waitFor(() => expect(lastCategoryCode).toBe('C002'));
  expect(await screen.findByText('C002 상품 1')).toBeInTheDocument();
});

test('순위 번호가 1위부터 순서대로 렌더된다', async () => {
  renderRanking('/ranking');

  await screen.findByText('전체 상품 1');
  expect(screen.getByText('1위')).toBeInTheDocument();
  expect(screen.getByText('2위')).toBeInTheDocument();
  expect(screen.getByText('3위')).toBeInTheDocument();
});

test('빈 배열 응답이면 EmptyState를 보여주고 그리드는 렌더하지 않는다', async () => {
  renderRanking('/ranking?category=EMPTY');

  expect(await screen.findByRole('status')).toBeInTheDocument();
  expect(screen.queryByText(/위$/)).not.toBeInTheDocument();
});

test('화살표 키로 탭 간 포커스가 이동하고(roving tabindex) Enter로 선택된다', async () => {
  renderRanking('/ranking');

  const allTab = await screen.findByRole('tab', { name: '전체' });
  allTab.focus();
  expect(allTab).toHaveAttribute('tabIndex', '0');

  fireEvent.keyDown(allTab, { key: 'ArrowRight' });

  const skinCareTab = screen.getByRole('tab', { name: '스킨케어' });
  expect(skinCareTab).toHaveFocus();
  expect(skinCareTab).toHaveAttribute('tabIndex', '0');
  expect(allTab).toHaveAttribute('tabIndex', '-1');
});

import { expect, it, test, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { createMemoryRouter, MemoryRouter, RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Layout } from './Layout';

function renderLayout() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Layout />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

/** createMemoryRouter로 Layout + 더미 자식 2개를 세우고 라우터 핸들을 돌려준다. */
function renderLayoutAt(initialPath: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      {
        path: '/',
        element: <Layout />,
        children: [
          { path: 'main', element: <div>더미 메인</div> },
          { path: 'cart', element: <div>더미 장바구니</div> },
          { path: 'goods', element: <div>더미 목록</div> },
        ],
      },
    ],
    { initialEntries: [initialPath] },
  );

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );

  return router;
}

async function navigateTo(router: ReturnType<typeof createMemoryRouter>, path: string) {
  await act(async () => {
    await router.navigate(path);
  });
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

it('pathname이 바뀌면 스크롤을 즉시(top:0, instant) 리셋한다', async () => {
  const scrollTo = vi.fn();
  vi.stubGlobal('scrollTo', scrollTo); // jsdom은 scrollTo 미구현 — 스텁이 곧 관측 지점
  const router = renderLayoutAt('/main');
  scrollTo.mockClear(); // 첫 렌더 호출은 관심 밖 — 전환만 검증한다
  await navigateTo(router, '/cart');
  expect(scrollTo).toHaveBeenCalledWith({ top: 0, left: 0, behavior: 'instant' });
});

it('같은 pathname에서 쿼리스트링만 바뀌면 리셋하지 않는다', async () => {
  const scrollTo = vi.fn();
  vi.stubGlobal('scrollTo', scrollTo);
  const router = renderLayoutAt('/goods?category=C002');
  scrollTo.mockClear();
  await navigateTo(router, '/goods?category=C002&sort=priceAsc');
  expect(scrollTo).not.toHaveBeenCalled();
});

import { expect, it, test, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Layout } from './Layout';

// Layout의 <ScrollRestoration>은 데이터 라우터 컨텍스트를 요구한다 —
// 이 파일의 모든 렌더는 실 라우터와 같은 createMemoryRouter로 세운다.
function renderLayout() {
  renderLayoutAt('/main');
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

// 스크롤 정책은 이제 <ScrollRestoration>이 소유한다. 뒤로가기 복원 자체는 세션 히스토리 키에
// 걸려 있어 jsdom 단위테스트로 증명할 수 없다 — 그 판정은 실스택 시나리오(W2)로 하고,
// 여기서는 "새 이동은 최상단"이 유지되는지만 본다.
it('새 pathname으로 이동하면 최상단으로 간다', async () => {
  const scrollTo = vi.fn();
  vi.stubGlobal('scrollTo', scrollTo); // jsdom은 scrollTo 미구현 — 스텁이 곧 관측 지점
  const router = renderLayoutAt('/main');
  scrollTo.mockClear(); // 첫 렌더 호출은 관심 밖 — 전환만 검증한다
  await navigateTo(router, '/cart');
  expect(scrollTo).toHaveBeenCalledWith(0, 0);
});

// 의도된 동작 변화: 쿼리스트링만 바뀌는 push도 새 히스토리 엔트리라 최상단으로 간다.
// 결과 집합이 바뀌었으니 목록을 처음부터 보는 게 맞다(페이지 전환의 스크롤은 GoodsList가
// 목록 상단으로 따로 처리한다).
it('쿼리스트링만 바뀌는 이동도 새 엔트리라 최상단으로 간다', async () => {
  const scrollTo = vi.fn();
  vi.stubGlobal('scrollTo', scrollTo);
  const router = renderLayoutAt('/goods?category=C002');
  scrollTo.mockClear();
  await navigateTo(router, '/goods?category=C002&sort=priceAsc');
  expect(scrollTo).toHaveBeenCalledWith(0, 0);
});

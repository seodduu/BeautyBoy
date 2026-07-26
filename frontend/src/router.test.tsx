import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Layout } from './components/layout/Layout';
import { Home } from './pages/Home';
import { Login } from './pages/Login';
import { Signup } from './pages/Signup';
import { Main } from './pages/Main';
import { GoodsList } from './pages/GoodsList';
import { Detail } from './pages/Detail';
import { Search } from './pages/Search';
import { Ranking } from './pages/Ranking';
import { OrderFail } from './pages/OrderFail';
import { RequireAuth } from './components/auth/RequireAuth';
import { ToastProvider } from './components/ui/ToastProvider';
import { useAuthStore } from './stores/authStore';

/**
 * 라우팅 스모크 테스트 — router.tsx가 createBrowserRouter를 쓰므로(테스트 환경에서 못 씀)
 * 같은 children 구성을 createMemoryRouter로 그대로 복제해 각 경로가 담당 페이지를 렌더하는지 확인한다.
 * dev 전용 /dev/components 라우트는 상용 흐름이 아니므로 여기서는 다루지 않는다.
 */
function buildRoutes() {
  return [
    {
      path: '/',
      element: <Layout />,
      children: [
        { index: true, element: <Home /> },
        { path: 'login', element: <Login /> },
        { path: 'signup', element: <Signup /> },
        {
          path: 'main',
          element: (
            <RequireAuth>
              <Main />
            </RequireAuth>
          ),
        },
        {
          path: 'goods',
          element: (
            <RequireAuth>
              <GoodsList />
            </RequireAuth>
          ),
        },
        {
          path: 'goods/:goodsNo',
          element: (
            <RequireAuth>
              <Detail />
            </RequireAuth>
          ),
        },
        {
          path: 'search',
          element: (
            <RequireAuth>
              <Search />
            </RequireAuth>
          ),
        },
        {
          path: 'ranking',
          element: (
            <RequireAuth>
              <Ranking />
            </RequireAuth>
          ),
        },
        // 토스 실패 리다이렉트 착지점 — API/인증 의존이 없으므로 RequireAuth로 감싸지 않는다.
        // (감쌌던 시절엔 리프레시 실패 상태로 돌아오면 실패 사유 대신 /login으로 튕겼다.)
        { path: 'order/fail', element: <OrderFail /> },
      ],
    },
  ];
}

function renderAt(initialEntry: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(buildRoutes(), { initialEntries: [initialEntry] });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

// ToastProvider(Detail이 useToast를 쓴다)가 prefers-reduced-motion 판정에 matchMedia를 쓰므로
// jsdom에 최소 구현을 채운다. 다른 페이지 테스트들과 동일한 패턴.
beforeEach(() => {
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

  // RequireAuth 가드를 통과시킨다 — 로그인 상태를 흉내낸 인증 스모크가 아니라
  // 라우팅 자체(각 경로 → 각 페이지)가 목표이므로 부트스트랩도 끝난 것으로 둔다.
  useAuthStore.setState({ accessToken: 'test-token', isBootstrapping: false });
});

describe('router — 상세·검색·랭킹 라우팅', () => {
  it('/search는 검색 페이지를 렌더한다', async () => {
    renderAt('/search');

    expect(await screen.findByRole('heading', { name: '검색', level: 1 })).toBeInTheDocument();
  });

  it('/ranking은 랭킹 페이지를 렌더한다', async () => {
    renderAt('/ranking');

    expect(await screen.findByRole('heading', { name: '랭킹', level: 1 })).toBeInTheDocument();
  });

  it('/goods/1은 상세 페이지를 렌더한다', async () => {
    renderAt('/goods/1');

    expect(await screen.findByRole('button', { name: '장바구니 담기' })).toBeInTheDocument();
  });

  it('/goods는 여전히 목록 페이지를 렌더한다(회귀 방지)', async () => {
    renderAt('/goods');

    expect(await screen.findAllByRole('link', { name: /No\./ })).not.toHaveLength(0);
  });
});

describe('router — /order/fail은 비로그인 상태에서도 렌더된다', () => {
  // 이 describe에서 setState로 비로그인 상태를 흉내내므로, 뒤에 오는 테스트가 앞선
  // beforeEach의 로그인 상태를 전제하지 않도록 원상 복구한다(App.test.tsx의 기존 패턴).
  afterEach(() => {
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
  });

  it('부트스트랩이 끝난 비로그인 상태여도 /login으로 튕기지 않고 실패 사유를 보여준다', async () => {
    useAuthStore.setState({ accessToken: null, isBootstrapping: false });

    renderAt('/order/fail?code=PAY_PROCESS_CANCELED&message=사용자가+결제를+취소했습니다');

    expect(await screen.findByText('사용자가 결제를 취소했습니다')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '로그인', level: 1 })).not.toBeInTheDocument();
  });
});

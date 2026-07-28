import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Layout } from '../layout/Layout';
import { RouteError } from './RouteError';

/** 렌더되면 즉시 예외를 던지는 더미 자식 — errorElement 경계를 트리거한다. */
function Boom(): never {
  throw new Error('DB 커넥션 풀 고갈: jdbc:mysql://localhost:3306/beautyboy');
}

/**
 * router.tsx가 세우는 구조(경로 없는 경계 + catch-all)를 그대로 복제한다.
 * createBrowserRouter는 테스트 환경에서 쓸 수 없으므로 createMemoryRouter로 세운다
 * (router.test.tsx·Layout.test.tsx와 같은 패턴).
 */
function renderAt(initialEntry: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      {
        path: '/',
        element: <Layout />,
        children: [
          {
            errorElement: <RouteError />,
            children: [
              { index: true, element: <div>홈 더미</div> },
              { path: 'boom', element: <Boom /> },
              { path: '*', element: <RouteError /> },
            ],
          },
        ],
      },
    ],
    { initialEntries: [initialEntry] },
  );

  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

describe('RouteError', () => {
  it('렌더 중 예외가 나도 헤더·푸터는 남는다', () => {
    renderAt('/boom');

    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByText('화면을 불러오지 못했어요')).toBeInTheDocument();
  });

  it('없는 주소는 404 문구를 낸다 — 예외 화면과 같은 컴포넌트다', () => {
    renderAt('/이런경로는없다');

    expect(screen.getByText('요청하신 페이지를 찾을 수 없어요')).toBeInTheDocument();
  });

  it('예외 메시지를 화면에 내지 않는다', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

    renderAt('/boom');

    expect(screen.queryByText(/jdbc/)).not.toBeInTheDocument();

    consoleError.mockRestore();
  });

  it('복구 경로를 둘 다 준다 — 다시 시도 + 홈으로', () => {
    renderAt('/boom');

    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '홈으로' })).toBeInTheDocument();
  });

  it('오류 컨테이너에 role="alert"과 h1이 있다', () => {
    renderAt('/boom');

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
  });
});

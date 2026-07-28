import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { Header } from './Header';
import { useAuthStore } from '../../stores/authStore';

function renderHeader() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/mypage']}>
        <Routes>
          <Route path="/mypage" element={<Header />} />
          <Route path="/" element={<div>HOME_MARKER</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

/* 랜딩(/)은 헤더가 다른 가지를 렌더한다(투명 오버레이 + 내비만). 위 renderHeader는 '/'를
   로그아웃 이동 확인용 HOME_MARKER로 잡아두므로, 경로를 지정해 헤더 자체를 그리는 헬퍼를 따로 둔다. */
function renderHeaderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Header />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function envelope<T>(data: T) {
  return { code: 'OK', message: 'success', data };
}

describe('Header — 로그인/로그아웃 UI', () => {
  beforeEach(() => {
    // 부트스트랩이 끝난 상태를 기본값으로 둔다 — 이 테스트들은 로그인/로그아웃 결과 UI만 본다.
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: false });
  });

  it('비로그인 상태에서는 "로그인" 링크가 보이고 로그아웃 버튼은 없다', () => {
    renderHeader();

    expect(screen.getByRole('link', { name: '로그인' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument();
  });

  it('로그인 상태에서는 로그아웃 버튼이 보이고, 클릭 시 서버에 로그아웃 요청 후 스토어가 비워지고 홈으로 이동한다', async () => {
    useAuthStore.getState().setAuth('token-abc', {
      id: 1,
      email: 'test@beautyboy.dev',
      nickname: '민수',
      grade: 'BRONZE',
    });

    let logoutCalled = false;
    server.use(
      http.post('/api/v1/auth/logout', () => {
        logoutCalled = true;
        return HttpResponse.json({ code: 'OK', message: '성공', data: null });
      }),
    );

    renderHeader();

    expect(screen.getByText('민수님')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '로그인' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }));

    await waitFor(() => expect(logoutCalled).toBe(true));
    await screen.findByText('HOME_MARKER');

    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useAuthStore.getState().member).toBeNull();
  });

  it('부트스트랩 진행 중에는 로그인/로그아웃 링크 대신 스켈레톤을 보여준다', () => {
    useAuthStore.setState({ isBootstrapping: true });

    renderHeader();

    expect(screen.queryByRole('link', { name: '로그인' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument();
  });
});

describe('Header — 장바구니 배지', () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: false });
  });

  it('로그인 상태에서 장바구니에 3줄이 있으면 배지가 3을 표시한다', async () => {
    useAuthStore.getState().setAuth('token-abc', {
      id: 1,
      email: 'test@beautyboy.dev',
      nickname: '민수',
      grade: 'BRONZE',
    });
    server.use(
      http.get('/api/v1/cart/items', () =>
        HttpResponse.json(
          envelope([
            { cartItemId: 1, goodsNo: 1, optionNo: null, goodsName: 'A', optionName: '', unitPrice: 1000, quantity: 1, lineAmount: 1000 },
            { cartItemId: 2, goodsNo: 2, optionNo: null, goodsName: 'B', optionName: '', unitPrice: 2000, quantity: 3, lineAmount: 6000 },
            { cartItemId: 3, goodsNo: 3, optionNo: null, goodsName: 'C', optionName: '', unitPrice: 3000, quantity: 1, lineAmount: 3000 },
          ]),
        ),
      ),
    );

    renderHeader();

    expect(await screen.findByText('3')).toBeInTheDocument();
  });

  it('비로그인 상태에서는 GET /cart/items를 호출하지 않는다', async () => {
    let cartItemsCalled = false;
    server.use(
      http.get('/api/v1/cart/items', () => {
        cartItemsCalled = true;
        return HttpResponse.json(envelope([]));
      }),
    );

    renderHeader();

    // 비동기 호출이 있었다면 발생할 시간을 주고, 그래도 호출되지 않았음을 단언한다.
    await waitFor(() => expect(screen.getByRole('link', { name: '장바구니' })).toBeInTheDocument());
    expect(cartItemsCalled).toBe(false);
  });

  it('장바구니 링크가 /cart를 가리키고, 접근성 이름에 "준비 중"이 없다', () => {
    renderHeader();

    const link = screen.getByRole('link', { name: '장바구니' });
    expect(link).toHaveAttribute('href', '/cart');
    expect(link.getAttribute('aria-label')).not.toContain('준비 중');
  });
});

describe('Header — 랜딩 내비', () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: false });
  });

  it('랜딩(/) 내비는 실제 라우트로 가는 링크다 — 자리표시 텍스트가 아니다', () => {
    renderHeaderAt('/');
    expect(screen.getByRole('link', { name: '루틴 가이드' })).toHaveAttribute('href', '/routine');
    expect(screen.getByRole('link', { name: '랭킹' })).toHaveAttribute('href', '/ranking');
    expect(screen.getByRole('link', { name: '전체 상품' })).toHaveAttribute('href', '/goods');
    expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login');
  });

  it('자리표시 항목(About/Work/Services/Packages)은 더 이상 없다', () => {
    renderHeaderAt('/');
    for (const stale of ['About', 'Work', 'Services', 'Packages', 'Login']) {
      expect(screen.queryByText(stale)).not.toBeInTheDocument();
    }
  });
});

import { beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useSearchParams } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { Header } from './Header';
import { useAuthStore } from '../../stores/authStore';

beforeAll(() => {
  // Header가 769px 리사이즈 시 시트를 닫기 위해 matchMedia를 쓴다. jsdom에는 구현이 없다.
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    }),
  });
});

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

/** /search로 실제 이동했는지와 그때 실린 q를 화면에 드러내는 목적지 컴포넌트. */
function SearchRouteMarker() {
  const [params] = useSearchParams();
  return <div>{`SEARCH_ROUTE:${params.get('q') ?? ''}`}</div>;
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

describe('Header — 앱(로그인 이후) 내비', () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: false });
  });

  it('랜딩이 아닌 화면에서도 루틴 가이드·랭킹·전체 상품 링크가 실제 라우트를 가리킨다', () => {
    renderHeaderAt('/main');
    expect(screen.getByRole('link', { name: '루틴 가이드' })).toHaveAttribute('href', '/routine');
    expect(screen.getByRole('link', { name: '랭킹' })).toHaveAttribute('href', '/ranking');
    expect(screen.getByRole('link', { name: '전체 상품' })).toHaveAttribute('href', '/goods');
  });

  it('현재 위치와 일치하는 항목에 aria-current="page"가 붙는다', () => {
    renderHeaderAt('/ranking');
    expect(screen.getByRole('link', { name: '랭킹' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: '루틴 가이드' })).not.toHaveAttribute('aria-current');
  });

  it('우측 그룹이 루틴 가이드 → 랭킹 → 전체 상품 → 장바구니 → 프로필 → 로그아웃 순서다', () => {
    useAuthStore.getState().setAuth('token-abc', {
      id: 1,
      email: 'test@beautyboy.dev',
      nickname: '민수',
      grade: 'BRONZE',
    });

    renderHeaderAt('/main');

    // 로고·검색을 제외한 헤더 우측 내비의 실제 DOM 순서를 본다.
    const nav = screen.getByRole('navigation', { name: '주요 메뉴' });
    const labels = Array.from(nav.querySelectorAll('a, button')).map((el) =>
      el.textContent?.trim(),
    );
    expect(labels).toEqual(['루틴 가이드', '랭킹', '전체 상품', '장바구니', '민수님', '로그아웃']);
  });
});

describe('Header — 검색', () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: false });
  });

  /* 헤더 + /search 목적지를 함께 그려 실제 이동 결과(q 파라미터)를 눈으로 확인한다. */
  function renderHeaderWithSearchRoute() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    return render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/main']}>
          <Header />
          <Routes>
            <Route path="/main" element={<div />} />
            <Route path="/search" element={<SearchRouteMarker />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
  }

  it('헤더 검색은 자리표시 텍스트가 아니라 입력 가능한 검색창이다', () => {
    renderHeaderAt('/main');

    expect(screen.getByRole('searchbox', { name: '상품 검색' })).toBeInTheDocument();
    // "준비 중"이라고 이름 붙은 죽은 자리표시가 남아 있으면 안 된다.
    expect(screen.queryByLabelText('상품 검색(준비 중)')).not.toBeInTheDocument();
  });

  it('검색어를 넣고 제출하면 /search?q=로 이동한다', async () => {
    renderHeaderWithSearchRoute();

    fireEvent.change(screen.getByRole('searchbox', { name: '상품 검색' }), {
      target: { value: '크림' },
    });
    fireEvent.submit(screen.getByRole('search', { name: '상품 검색' }));

    expect(await screen.findByText('SEARCH_ROUTE:크림')).toBeInTheDocument();
  });

  it('검색 화면에서는 헤더 입력이 URL의 q를 그대로 보여준다 — 직전 검색어가 남지 않는다', () => {
    renderHeaderAt('/search?q=토너');

    expect(screen.getByRole('searchbox', { name: '상품 검색' })).toHaveValue('토너');
  });

  it('공백만 입력하면 이동하지 않는다 — 빈 검색은 서버가 400을 준다', () => {
    renderHeaderWithSearchRoute();

    fireEvent.change(screen.getByRole('searchbox', { name: '상품 검색' }), {
      target: { value: '   ' },
    });
    fireEvent.submit(screen.getByRole('search', { name: '상품 검색' }));

    expect(screen.queryByText(/^SEARCH_ROUTE:/)).not.toBeInTheDocument();
  });
});

describe('모바일 햄버거 내비', () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: false });
  });

  it('메뉴 버튼은 aria-expanded=false로 시작한다', () => {
    renderHeaderAt('/main');

    const toggle = screen.getByRole('button', { name: '메뉴 열기' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
  });

  it('메뉴를 열면 PRIMARY_NAV 세 링크가 모두 보인다 — 768px 이하에서 사라지던 것들이다', async () => {
    renderHeaderAt('/main');

    fireEvent.click(screen.getByRole('button', { name: '메뉴 열기' }));

    const sheet = screen.getByRole('navigation', { name: '메뉴' });
    expect(within(sheet).getByRole('link', { name: '루틴 가이드' })).toHaveAttribute(
      'href',
      '/routine',
    );
    expect(within(sheet).getByRole('link', { name: '랭킹' })).toHaveAttribute('href', '/ranking');
    expect(within(sheet).getByRole('link', { name: '전체 상품' })).toHaveAttribute(
      'href',
      '/goods',
    );
  });

  it('링크를 누르면 메뉴가 닫힌다', async () => {
    renderHeaderAt('/main');

    fireEvent.click(screen.getByRole('button', { name: '메뉴 열기' }));
    const sheet = screen.getByRole('navigation', { name: '메뉴' });
    fireEvent.click(within(sheet).getByRole('link', { name: '랭킹' }));

    expect(screen.queryByRole('navigation', { name: '메뉴' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '메뉴 열기' })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
  });

  it('버튼을 다시 누르면 닫힌다 (aria-expanded=false)', async () => {
    renderHeaderAt('/main');

    const toggle = screen.getByRole('button', { name: '메뉴 열기' });
    fireEvent.click(toggle);
    expect(screen.getByRole('button', { name: '메뉴 닫기' })).toHaveAttribute(
      'aria-expanded',
      'true',
    );

    fireEvent.click(screen.getByRole('button', { name: '메뉴 닫기' }));
    expect(screen.getByRole('button', { name: '메뉴 열기' })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
    expect(screen.queryByRole('navigation', { name: '메뉴' })).not.toBeInTheDocument();
  });

  it('Esc를 누르면 닫힌다', async () => {
    renderHeaderAt('/main');

    fireEvent.click(screen.getByRole('button', { name: '메뉴 열기' }));
    expect(screen.getByRole('navigation', { name: '메뉴' })).toBeInTheDocument();

    fireEvent.keyDown(document, { key: 'Escape' });

    expect(screen.queryByRole('navigation', { name: '메뉴' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '메뉴 열기' })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
  });

  it('라우트가 바뀌면 닫힌다 — 이동 후에도 시트가 남으면 안 된다', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/main']}>
          <Routes>
            <Route path="/main" element={<Header />} />
            <Route path="/ranking" element={<Header />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    fireEvent.click(screen.getByRole('button', { name: '메뉴 열기' }));
    const sheet = screen.getByRole('navigation', { name: '메뉴' });
    fireEvent.click(within(sheet).getByRole('link', { name: '랭킹' }));

    await waitFor(() =>
      expect(screen.queryByRole('navigation', { name: '메뉴' })).not.toBeInTheDocument(),
    );
  });

  it('장바구니와 계정은 햄버거 안이 아니라 상단바에 남는다', () => {
    useAuthStore.getState().setAuth('token-abc', {
      id: 1,
      email: 'test@beautyboy.dev',
      nickname: '민수',
      grade: 'BRONZE',
    });

    renderHeaderAt('/main');

    // 메뉴가 닫힌 상태에서도 장바구니·닉네임 링크는 상단바에 그대로 보인다.
    expect(screen.getByRole('link', { name: '장바구니' })).toBeInTheDocument();
    expect(screen.getByText('민수님')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: '메뉴' })).not.toBeInTheDocument();
  });

  it('랜딩 헤더에서도 LANDING_NAV 네 개가 메뉴로 들어간다', () => {
    renderHeaderAt('/');

    fireEvent.click(screen.getByRole('button', { name: '메뉴 열기' }));

    const sheet = screen.getByRole('navigation', { name: '메뉴' });
    expect(within(sheet).getByRole('link', { name: '루틴 가이드' })).toHaveAttribute(
      'href',
      '/routine',
    );
    expect(within(sheet).getByRole('link', { name: '랭킹' })).toHaveAttribute('href', '/ranking');
    expect(within(sheet).getByRole('link', { name: '전체 상품' })).toHaveAttribute(
      'href',
      '/goods',
    );
    expect(within(sheet).getByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login');
  });
});

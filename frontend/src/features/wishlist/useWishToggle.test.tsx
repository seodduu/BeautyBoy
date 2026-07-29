import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { ToastProvider } from '../../components/ui/ToastProvider';
import { GoodsCard } from '../../components/goods/GoodsCard';
import { useAuthStore } from '../../stores/authStore';
import type { SearchResultItem } from '../../types/search';
import { useWishToggle } from './useWishToggle';
import { useWishStore } from './wishStore';

const ITEM: SearchResultItem = {
  goodsNo: 1,
  brandName: '어반메일',
  name: '그린티 토너',
  thumbnailUrl: 'data:image/svg+xml;utf8,test',
  listPrice: 20000,
  salePrice: 18000,
  discountRate: 10,
  badges: [],
  rating: 4.5,
  reviewCount: 12,
  wished: false,
  todayDreamAvailable: false,
  tags: [],
};

/** 카드 하나 + 훅 배선만 있는 최소 하네스 — 이 훅의 계약(호출·낙관적 표시·되돌리기)만 본다. */
function WishCardHarness({ item = ITEM }: { item?: SearchResultItem }) {
  const toggleWish = useWishToggle();
  return <GoodsCard item={item} onWishToggle={toggleWish} />;
}

function renderHarness(item?: SearchResultItem) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/goods']}>
          <Routes>
            <Route path="/goods" element={<WishCardHarness item={item} />} />
            <Route path="/login" element={<div>LOGIN_MARKER</div>} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

function login() {
  useAuthStore.getState().setAuth('token-abc', {
    id: 1,
    email: 'test@beautyboy.dev',
    nickname: '민수',
    grade: 'BRONZE',
  });
}

beforeEach(() => {
  // ToastProvider가 prefers-reduced-motion을 본다 — jsdom에는 matchMedia 구현이 없다.
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;

  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: false });
  useWishStore.getState().reset();
});

describe('useWishToggle — 카드 하트의 실제 배선', () => {
  it('로그인 상태에서 하트를 누르면 POST /wishlist/{goodsNo}를 호출한다', async () => {
    login();
    let posted: string | null = null;
    server.use(
      http.post('/api/v1/wishlist/:goodsNo', ({ params }) => {
        posted = String(params.goodsNo);
        return new HttpResponse(null, { status: 201 });
      }),
    );

    renderHarness();

    fireEvent.click(screen.getByRole('button', { name: '찜하기' }));

    await waitFor(() => expect(posted).toBe('1'));
  });

  it('하트는 서버 응답을 기다리지 않고 즉시 켜진다 — 누른 티가 나야 한다', async () => {
    login();
    server.use(
      http.post('/api/v1/wishlist/:goodsNo', () => new HttpResponse(null, { status: 201 })),
    );

    renderHarness();

    fireEvent.click(screen.getByRole('button', { name: '찜하기' }));

    expect(await screen.findByRole('button', { name: '찜 해제' })).toBeInTheDocument();
  });

  it('이미 찜한 상품의 하트를 누르면 DELETE를 호출한다', async () => {
    login();
    let deleted: string | null = null;
    server.use(
      http.delete('/api/v1/wishlist/:goodsNo', ({ params }) => {
        deleted = String(params.goodsNo);
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderHarness({ ...ITEM, wished: true });

    fireEvent.click(screen.getByRole('button', { name: '찜 해제' }));

    await waitFor(() => expect(deleted).toBe('1'));
  });

  it('실패하면 하트가 원래대로 돌아가고 토스트로 알린다 — 조용히 켜진 채 두지 않는다', async () => {
    login();
    server.use(
      http.post('/api/v1/wishlist/:goodsNo', () => new HttpResponse(null, { status: 500 })),
    );

    renderHarness();

    fireEvent.click(screen.getByRole('button', { name: '찜하기' }));

    await screen.findByText('찜에 실패했어요. 다시 시도해 주세요');
    expect(await screen.findByRole('button', { name: '찜하기' })).toBeInTheDocument();
  });

  it('비로그인 상태에서는 요청을 보내지 않고 로그인으로 보낸다', async () => {
    let posted = false;
    server.use(
      http.post('/api/v1/wishlist/:goodsNo', () => {
        posted = true;
        return new HttpResponse(null, { status: 201 });
      }),
    );

    renderHarness();

    fireEvent.click(screen.getByRole('button', { name: '찜하기' }));

    await screen.findByText('LOGIN_MARKER');
    expect(posted).toBe(false);
  });
});

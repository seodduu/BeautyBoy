import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { ToastProvider } from '../../components/ui/ToastProvider';
import { MyWishlist } from './MyWishlist';
import * as wishlistApi from '../../api/wishlist';
import type { GoodsDetail } from '../../types/detail';

function envelope<T>(data: T) {
  return { code: 'OK', message: 'success', data };
}

const GOODS_DETAIL_1: GoodsDetail = {
  goodsNo: 1,
  brandName: '어반메일',
  brandId: 1,
  name: '그린티 토너',
  summary: '',
  categoryCode: 'C001001',
  categoryPath: [],
  thumbnailUrl: 'data:image/svg+xml;utf8,test',
  listPrice: 20000,
  salePrice: 18000,
  discountRate: 10,
  badges: [],
  status: 'ON_SALE',
  options: [],
  rating: 4.5,
  reviewCount: 12,
  wished: true,
  todayDreamAvailable: false,
  tags: [],
};

function registerHandlers(options: { wishlistGoodsNos?: number[] } = {}) {
  const { wishlistGoodsNos = [1] } = options;
  server.use(
    http.get('/api/v1/wishlist', () =>
      HttpResponse.json(envelope(wishlistGoodsNos.map((goodsNo) => ({ goodsNo })))),
    ),
    http.get('/api/v1/goods/1', () => HttpResponse.json(envelope(GOODS_DETAIL_1))),
    http.delete('/api/v1/wishlist/1', () => new HttpResponse(null, { status: 204 })),
  );
}

function renderMyWishlist() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/mypage/wishlist']}>
          <MyWishlist />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

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
});

describe('MyWishlist — 마이페이지 찜', () => {
  it('찜 목록에서 하트를 끄면 카드가 사라진다', async () => {
    registerHandlers();
    const removeWishSpy = vi.spyOn(wishlistApi, 'removeWish');

    renderMyWishlist();

    expect(await screen.findByText('그린티 토너')).toBeInTheDocument();

    fireEvent.click(await screen.findByRole('button', { name: /찜 해제/ }));

    expect(removeWishSpy).toHaveBeenCalledWith(1);

    // 해제 후 목록이 빈 상태로 재조회된다 — 카드가 화면에서 사라진다.
    server.use(http.get('/api/v1/wishlist', () => HttpResponse.json(envelope([]))));
    await waitFor(() => expect(screen.queryByText('그린티 토너')).not.toBeInTheDocument());

    removeWishSpy.mockRestore();
  });
});

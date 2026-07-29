import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { ToastProvider } from '../../components/ui/ToastProvider';
import { MyWishlist } from './MyWishlist';
import * as wishlistApi from '../../api/wishlist';
import { useAuthStore } from '../../stores/authStore';
import { useWishStore } from '../../features/wishlist/wishStore';
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

/**
 * GET/DELETE 핸들러가 같은 배열을 공유한다 — DELETE 성공 시 그 자리에서 goodsNo를 지워
 * 이후의 GET(재조회)이 실제 서버처럼 갱신된 목록을 돌려준다. 두 핸들러를 각자
 * `server.use()`로 시점을 맞춰 흉내 내면(예: DELETE 성공을 기다렸다가 GET 핸들러를
 * 교체) react-query의 invalidate → refetch가 그 교체보다 먼저 끝나버리는 경합이 생긴다.
 */
function registerHandlers(options: { wishlistGoodsNos?: number[]; deleteStatus?: number } = {}) {
  const { wishlistGoodsNos = [1], deleteStatus = 204 } = options;
  const goodsNos = [...wishlistGoodsNos];
  server.use(
    http.get('/api/v1/wishlist', () =>
      HttpResponse.json(envelope(goodsNos.map((goodsNo) => ({ goodsNo })))),
    ),
    http.get('/api/v1/goods/1', () => HttpResponse.json(envelope(GOODS_DETAIL_1))),
    http.delete('/api/v1/wishlist/1', () => {
      if (deleteStatus >= 400) {
        return new HttpResponse(null, { status: deleteStatus });
      }
      const index = goodsNos.indexOf(1);
      if (index !== -1) goodsNos.splice(index, 1);
      return new HttpResponse(null, { status: 204 });
    }),
  );
}

function renderMyWishlist() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/mypage/wishlist']}>
          <MyWishlist />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
  return queryClient;
}

beforeEach(() => {
  // 앞 테스트에서 누른 하트의 오버레이가 다음 테스트로 새지 않게 비운다.
  useWishStore.getState().reset();

  /* 이 화면은 RequireAuth 뒤에 있어 실서비스에서는 항상 로그인 상태다. 해제가 공용
     useWishToggle을 타므로(비로그인이면 로그인으로 보낸다) 테스트도 그 전제를 맞춰준다. */
  useAuthStore.getState().setAuth('token-abc', {
    id: 1,
    email: 'test@beautyboy.dev',
    nickname: '민수',
    grade: 'BRONZE',
  });

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

    await waitFor(() => expect(removeWishSpy).toHaveBeenCalledWith(1));

    // 해제 후 목록이 빈 상태로 재조회된다 — 카드가 화면에서 사라진다.
    await waitFor(() => expect(screen.queryByText('그린티 토너')).not.toBeInTheDocument());

    removeWishSpy.mockRestore();
  });

  it('찜 해제가 실패하면 토스트로 알린다 — 조용히 사라지지 않는다', async () => {
    registerHandlers({ deleteStatus: 500 });

    renderMyWishlist();

    expect(await screen.findByText('그린티 토너')).toBeInTheDocument();

    fireEvent.click(await screen.findByRole('button', { name: /찜 해제/ }));

    const toastMessage = await screen.findByText('찜 해제에 실패했어요. 다시 시도해 주세요');
    expect(toastMessage.closest('[role="status"]')).toHaveTextContent(
      '찜 해제에 실패했어요. 다시 시도해 주세요',
    );
  });

  it('찜 해제가 실패해도 카드가 목록에 남는다 — 낙관적 제거를 하지 않는다', async () => {
    registerHandlers({ deleteStatus: 500 });

    renderMyWishlist();

    expect(await screen.findByText('그린티 토너')).toBeInTheDocument();

    fireEvent.click(await screen.findByRole('button', { name: /찜 해제/ }));

    await screen.findByText('찜 해제에 실패했어요. 다시 시도해 주세요');
    expect(screen.getByText('그린티 토너')).toBeInTheDocument();
  });
});

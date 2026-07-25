import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { useAuthStore } from '../../stores/authStore';
import { RequireAdmin } from '../../components/auth/RequireAdmin';
import { ToastProvider } from '../../components/ui/ToastProvider';
import { AdminGoods } from './AdminGoods';
import * as adminApi from '../../api/admin';
import type { AdminGoodsListItem } from '../../api/admin';

function envelope<T>(data: T) {
  return { code: 'OK', message: 'success', data };
}

const VISIBLE_GOODS: AdminGoodsListItem = {
  goodsNo: 1,
  brandName: '뷰티보이랩',
  name: '그린티 토너',
  thumbnailUrl: '/thumb1.jpg',
  listPrice: 20000,
  salePrice: 18000,
  discountRate: 10,
  badges: [],
  rating: 4.5,
  reviewCount: 10,
  wished: false,
  todayDreamAvailable: false,
  status: 'ON_SALE',
};

const HIDDEN_GOODS: AdminGoodsListItem = {
  goodsNo: 2,
  brandName: '뷰티보이랩',
  name: '단종 세럼',
  thumbnailUrl: '/thumb2.jpg',
  listPrice: 15000,
  salePrice: 12000,
  discountRate: 20,
  badges: [],
  rating: 4.0,
  reviewCount: 3,
  wished: false,
  todayDreamAvailable: false,
  status: 'HIDDEN',
};

function pageOf(content: AdminGoodsListItem[]) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    hasNext: false,
  };
}

function loginAs(role: 'USER' | 'ADMIN') {
  useAuthStore.getState().setBootstrapping(false);
  useAuthStore.getState().setAuth('token', {
    id: role === 'ADMIN' ? 99 : 1,
    email: `${role.toLowerCase()}@beautyboy.dev`,
    nickname: role === 'ADMIN' ? '관리자' : '민수',
    grade: 'BRONZE',
    role,
  });
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route
              path="/admin/goods"
              element={
                <RequireAdmin>
                  <AdminGoods />
                </RequireAdmin>
              }
            />
            <Route path="/main" element={<p>뷰티보이 메인</p>} />
          </Routes>
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

afterEach(() => {
  useAuthStore.getState().clear();
  vi.restoreAllMocks();
});

describe('AdminGoods — 관리자 상품 관리', () => {
  it('ADMIN이 아니면 /admin은 메인으로 돌려보낸다', async () => {
    loginAs('USER');
    server.use(http.get('/api/v1/admin/goods', () => HttpResponse.json(envelope(pageOf([VISIBLE_GOODS])))));

    renderAt('/admin/goods');

    expect(await screen.findByText(/뷰티보이/)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '상품 관리' })).not.toBeInTheDocument();
  });

  it('ADMIN은 상품 목록에서 숨김 상품도 본다', async () => {
    loginAs('ADMIN');
    server.use(
      http.get('/api/v1/admin/goods', () => HttpResponse.json(envelope(pageOf([VISIBLE_GOODS, HIDDEN_GOODS])))),
    );

    renderAt('/admin/goods');

    expect(await screen.findByText('숨김')).toBeInTheDocument();
    expect(screen.getByText('단종 세럼')).toBeInTheDocument();
  });

  it('삭제를 누르면 확인을 거친 뒤 DELETE를 부르고 목록을 다시 읽는다', async () => {
    loginAs('ADMIN');
    let deleted = false;
    server.use(
      http.get('/api/v1/admin/goods', () =>
        HttpResponse.json(envelope(pageOf(deleted ? [] : [VISIBLE_GOODS]))),
      ),
      http.delete('/api/v1/admin/goods/:goodsNo', () => {
        deleted = true;
        return HttpResponse.json(envelope(null));
      }),
    );
    const deleteGoodsSpy = vi.spyOn(adminApi, 'deleteAdminGoods');

    renderAt('/admin/goods');

    fireEvent.click(await screen.findByRole('button', { name: /삭제/ }));
    fireEvent.click(screen.getByRole('button', { name: '확인' }));

    await waitFor(() => expect(deleteGoodsSpy).toHaveBeenCalledWith(1));
    await waitFor(() => expect(screen.queryByText('그린티 토너')).not.toBeInTheDocument());
  });
});

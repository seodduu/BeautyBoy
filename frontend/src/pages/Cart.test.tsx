import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { ToastProvider } from '../components/ui/ToastProvider';
import { Cart } from './Cart';
import * as cartApi from '../api/cart';
import type { CartItem } from '../api/cart';
import type { CompatCheckResult } from '../api/compat';

function envelope<T>(data: T) {
  return { code: 'OK', message: 'success', data };
}

const fixtureThumbnailUrl = '/images/goods/greentea-toner.jpg';

const TWO_ITEMS: CartItem[] = [
  {
    cartItemId: 1,
    goodsNo: 1,
    optionNo: 11,
    goodsName: '그린티 토너',
    optionName: '기본',
    unitPrice: 20000,
    quantity: 2,
    lineAmount: 40000,
    thumbnailUrl: fixtureThumbnailUrl,
    stock: 30,
  },
  {
    // stock=1·quantity=1 — 스텝퍼 상한 min(재고, 99) 검증용 저재고 항목
    cartItemId: 2,
    goodsNo: 2,
    optionNo: null,
    goodsName: '저자극 클렌저',
    optionName: '',
    unitPrice: 3000,
    quantity: 1,
    lineAmount: 3000,
    thumbnailUrl: null,
    stock: 1,
  },
];

const ONE_ITEM: CartItem[] = [
  {
    cartItemId: 1,
    goodsNo: 1,
    optionNo: 11,
    goodsName: '그린티 토너',
    optionName: '기본',
    unitPrice: 20000,
    quantity: 2,
    lineAmount: 40000,
    thumbnailUrl: fixtureThumbnailUrl,
    stock: 30,
  },
];

const OK_RESULT: CompatCheckResult = { overall: 'OK', findings: [] };

function registerHandlers(options: { items?: CartItem[]; compat?: CompatCheckResult } = {}) {
  const { items = TWO_ITEMS, compat = OK_RESULT } = options;
  server.use(
    http.get('/api/v1/cart/items', () => HttpResponse.json(envelope(items))),
    http.post('/api/v1/compat/check', () => HttpResponse.json(envelope(compat))),
    http.patch('/api/v1/cart/items/:cartItemId', () =>
      HttpResponse.json(envelope(null)),
    ),
    http.delete('/api/v1/cart/items/:cartItemId', () =>
      HttpResponse.json(envelope(null)),
    ),
  );
}

function renderCart() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/cart']}>
          <Routes>
            <Route path="/cart" element={<Cart />} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

// ToastProvider가 prefers-reduced-motion 판정에 matchMedia를 쓰므로 jsdom에 최소 구현을 채운다.
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

describe('Cart — 장바구니', () => {
  it('장바구니 라인과 합계를 보여준다', async () => {
    registerHandlers();

    renderCart();

    expect(await screen.findByText('그린티 토너')).toBeInTheDocument();
    expect(screen.getByText('저자극 클렌저')).toBeInTheDocument();
    // 합계는 서버 lineAmount의 합(40000 + 3000) — 프론트가 unitPrice*quantity를 다시 곱하지 않는다.
    expect(screen.getByTestId('cart-total')).toHaveTextContent('43,000');
  });

  it('수량을 바꾸면 PATCH를 부르고 합계가 갱신된다', async () => {
    // PATCH가 실제로 상태를 바꾸고, 이후 invalidateQueries가 트리거하는 재조회(GET)가
    // 그 변경을 반영해 돌려주는 걸 함께 확인하기 위해 서버측 상태를 흉내낸다.
    let state: CartItem = { ...ONE_ITEM[0] };
    server.use(
      http.get('/api/v1/cart/items', () => HttpResponse.json(envelope([state]))),
      http.post('/api/v1/compat/check', () => HttpResponse.json(envelope(OK_RESULT))),
      http.patch('/api/v1/cart/items/:cartItemId', async ({ request }) => {
        const { quantity } = (await request.json()) as { quantity: number };
        state = { ...state, quantity, lineAmount: state.unitPrice * quantity };
        return HttpResponse.json(envelope(null));
      }),
    );
    const updateCartQuantitySpy = vi.spyOn(cartApi, 'updateCartQuantity');

    renderCart();

    await screen.findByTestId('cart-total');
    fireEvent.click(await screen.findByRole('button', { name: '수량 늘리기' }));

    await waitFor(() => expect(updateCartQuantitySpy).toHaveBeenCalledWith(1, 3));
    // 단가 20000 * 수량 3 = 60,000 — PATCH 이후 재조회된 lineAmount 그대로다.
    await waitFor(() => expect(screen.getByTestId('cart-total')).toHaveTextContent('60,000'));

    updateCartQuantitySpy.mockRestore();
  });

  it('삭제하면 라인이 사라지고 토스트가 뜬다', async () => {
    registerHandlers({ items: ONE_ITEM });

    renderCart();

    await screen.findByText('그린티 토너');

    server.use(
      http.delete('/api/v1/cart/items/:cartItemId', () => HttpResponse.json(envelope(null))),
      http.get('/api/v1/cart/items', () => HttpResponse.json(envelope([]))),
    );

    fireEvent.click(screen.getByRole('button', { name: '삭제' }));

    await waitFor(() => expect(screen.queryByText('그린티 토너')).not.toBeInTheDocument());
    expect(await screen.findByText('삭제했어요')).toBeInTheDocument();
  });

  it('장바구니가 비면 EmptyState를 보여주고 주문하기 버튼을 감춘다', async () => {
    registerHandlers({ items: [] });

    renderCart();

    // role="status"는 EmptyState뿐 아니라 항상 떠 있는 토스트 리전도 공유하므로
    // 빈 상태 문구로 먼저 좁힌 뒤 그 조상이 status 리전인지 확인한다.
    const emptyMessage = await screen.findByText(/비어 있/);
    expect(emptyMessage.closest('[role="status"]')).toHaveTextContent(/비어 있/);
    expect(screen.queryByRole('button', { name: '주문하기' })).not.toBeInTheDocument();
  });

  it('장바구니 줄에 썸네일과 개당 가격이 보인다', async () => {
    registerHandlers();

    renderCart();

    const line = await screen.findByTestId('cart-line-1');
    // alt=""(장식 이미지 — 상품명이 바로 옆에 있다)라 접근성 role은 presentation이다
    expect(within(line).getByRole('presentation')).toHaveAttribute('src', fixtureThumbnailUrl);
    expect(line).toHaveTextContent('개당 20,000원'); // unitPrice 표기 형식이 사양이다
  });

  it('수량 스텝퍼 상한은 min(재고, 99)다 — 재고 1이면 1에서 + 버튼이 비활성', async () => {
    registerHandlers();

    renderCart();

    const line = await screen.findByTestId('cart-line-2'); // stock=1, quantity=1
    expect(within(line).getByRole('button', { name: '수량 늘리기' })).toBeDisabled();
  });

  it('합계 라벨은 "결제 예상 금액"이고 배송비 안내가 붙는다', async () => {
    registerHandlers();

    renderCart();

    expect(await screen.findByText('결제 예상 금액')).toBeInTheDocument();
    expect(screen.getByText('배송비는 주문서에서 계산됩니다')).toBeInTheDocument();
  });

  it('CONFLICT면 경고 배너를 보여주고 이유를 읽어준다', async () => {
    registerHandlers({
      compat: {
        overall: 'CONFLICT',
        findings: [
          {
            verdict: 'CONFLICT',
            categoryA: 'AHA',
            categoryB: '레티노이드',
            reason: '자극 중첩',
            goodsNos: [1, 2],
          },
        ],
      },
    });

    renderCart();

    const banner = await screen.findByRole('alert');
    expect(banner).toHaveTextContent('자극 중첩');
  });

  it('OK면 배너를 그리지 않는다', async () => {
    registerHandlers();

    renderCart();

    await screen.findByText('그린티 토너');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('CONFLICT여도 주문하기는 막지 않는다', async () => {
    registerHandlers({
      compat: {
        overall: 'CONFLICT',
        findings: [
          {
            verdict: 'CONFLICT',
            categoryA: 'AHA',
            categoryB: '레티노이드',
            reason: '자극 중첩',
            goodsNos: [1, 2],
          },
        ],
      },
    });

    renderCart();

    await screen.findByRole('alert');
    expect(await screen.findByRole('button', { name: '주문하기' })).toBeEnabled();
  });
});

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { ToastProvider } from '../components/ui/ToastProvider';
import { Order } from './Order';
import * as orderApi from '../api/order';
import * as tossApi from '../features/payment/toss';
import { useAuthStore } from '../stores/authStore';
import type { CartItem } from '../api/cart';
import type { Address } from '../api/member';

function envelope<T>(data: T) {
  return { code: 'OK', message: '성공', data };
}

const CART_ITEMS: CartItem[] = [
  {
    cartItemId: 1,
    goodsNo: 1,
    optionNo: 11,
    goodsName: '그린티 토너',
    optionName: '기본',
    unitPrice: 20000,
    quantity: 2,
    lineAmount: 40000,
    thumbnailUrl: null,
    stock: 30,
  },
  {
    cartItemId: 2,
    goodsNo: 2,
    optionNo: null,
    goodsName: '저자극 클렌저',
    optionName: '',
    unitPrice: 1500,
    quantity: 2,
    lineAmount: 3000,
    thumbnailUrl: null,
    stock: 2147483647,
  },
];

const DEFAULT_ADDRESS: Address = {
  id: 1,
  receiver: '김민수',
  phone: '01012345678',
  zipcode: '06236',
  address1: '서울특별시 강남구 테헤란로 1',
  address2: '101동 202호',
  isDefault: true,
};

function registerHandlers(
  options: { addresses?: Address[]; items?: CartItem[] } = {},
) {
  const { addresses = [DEFAULT_ADDRESS], items = CART_ITEMS } = options;
  server.use(
    http.get('/api/v1/cart/items', () => HttpResponse.json(envelope(items))),
    http.get('/api/v1/members/me/addresses', () => HttpResponse.json(envelope(addresses))),
  );
}

function renderOrder() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/order']}>
          <Routes>
            <Route path="/order" element={<Order />} />
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

  useAuthStore.setState({
    accessToken: 'mock-token',
    member: { id: 1, email: 'test@beautyboy.dev', nickname: '테스터', grade: 'BRONZE' },
    isBootstrapping: false,
  });
});

describe('Order — 주문서', () => {
  it('기본배송지가 있으면 자동으로 선택돼 있다', async () => {
    registerHandlers();

    renderOrder();

    expect(await screen.findByRole('radio', { name: /집 · 서울/ })).toBeChecked();
  });

  it('배송지가 없으면 직접 입력 폼을 펼친다', async () => {
    registerHandlers({ addresses: [] });

    renderOrder();

    expect(await screen.findByLabelText('받는 분')).toBeInTheDocument();
  });

  it('받는 분 정보가 비면 결제하기가 막히고 에러가 role=alert로 뜬다', async () => {
    registerHandlers({ addresses: [] });
    const createOrderSpy = vi.spyOn(orderApi, 'createOrder');

    renderOrder();

    fireEvent.click(await screen.findByRole('button', { name: '결제하기' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/받는 분/);
    expect(createOrderSpy).not.toHaveBeenCalled();

    createOrderSpy.mockRestore();
  });

  it('결제하기는 주문을 만든 뒤 서버가 준 payableAmount로 결제창을 연다', async () => {
    registerHandlers();
    // createOrder → { orderNo: 'ORD-1', payableAmount: 43000 } (화면 합계 43000과 일부러 다른 값을 준다는
    // 브리프 의도를 살리기 위해 cart 합계와는 별개로 서버가 이 값을 그대로 내려주는 걸 검증한다)
    server.use(
      http.post('/api/v1/orders', () =>
        HttpResponse.json(envelope({ orderNo: 'ORD-1', payableAmount: 43000 }), { status: 201 }),
      ),
    );
    const requestTossPaymentSpy = vi.spyOn(tossApi, 'requestTossPayment').mockResolvedValue(undefined);

    renderOrder();

    fireEvent.click(await screen.findByRole('button', { name: '결제하기' }));

    await waitFor(() =>
      expect(requestTossPaymentSpy).toHaveBeenCalledWith(
        expect.objectContaining({ orderNo: 'ORD-1', amount: 43000 }),
      ),
    );

    requestTossPaymentSpy.mockRestore();
  });

  it('주문 생성이 재고 부족으로 실패하면 결제창을 열지 않고 에러를 보여준다', async () => {
    registerHandlers();
    server.use(
      http.post('/api/v1/orders', () =>
        HttpResponse.json(
          { code: 'ORDER_OUT_OF_STOCK', message: '재고가 부족한 상품이 있습니다', detail: null },
          { status: 409 },
        ),
      ),
    );
    const requestTossPaymentSpy = vi.spyOn(tossApi, 'requestTossPayment');

    renderOrder();

    fireEvent.click(await screen.findByRole('button', { name: '결제하기' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/재고/);
    expect(requestTossPaymentSpy).not.toHaveBeenCalled();

    requestTossPaymentSpy.mockRestore();
  });

  it('결제하기를 연타해도 주문은 한 번만 생성된다', async () => {
    registerHandlers();
    server.use(
      http.post('/api/v1/orders', () =>
        HttpResponse.json(envelope({ orderNo: 'ORD-1', payableAmount: 43000 }), { status: 201 }),
      ),
    );
    const createOrderSpy = vi.spyOn(orderApi, 'createOrder');
    vi.spyOn(tossApi, 'requestTossPayment').mockResolvedValue(undefined);

    renderOrder();

    const btn = await screen.findByRole('button', { name: '결제하기' });
    fireEvent.click(btn);
    fireEvent.click(btn);

    await waitFor(() => expect(createOrderSpy).toHaveBeenCalled());
    expect(createOrderSpy).toHaveBeenCalledTimes(1);

    createOrderSpy.mockRestore();
  });
});

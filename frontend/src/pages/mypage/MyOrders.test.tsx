import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { MyOrders } from './MyOrders';
import type { Address } from '../../api/member';
import type { OrderDetail, OrderSummary } from '../../api/order';

function envelope<T>(data: T) {
  return { code: 'OK', message: 'success', data };
}

const ORDER_SUMMARY: OrderSummary = {
  orderNo: 'ORD-1',
  status: 'PAID',
  representativeGoodsName: '그린티 토너',
  itemCount: 3,
  payableAmount: 58000,
  orderedAt: '2026-07-20T10:00:00',
};

const CURRENT_MEMBER_ADDRESS: Address = {
  id: 1,
  receiver: '김민수',
  phone: '01011112222',
  zipcode: '06236',
  address1: '서울특별시 강남구 테헤란로 1',
  address2: '101동 202호',
  isDefault: true,
};

// 주문 시점 스냅샷 — 현재 회원의 배송지(위 CURRENT_MEMBER_ADDRESS)와 의도적으로 다른 값이다.
// 화면이 이 값을 보여주면 스냅샷을 쓴다는 뜻이고, 위 값을 보여주면 현재 프로필을 잘못 참조한다는 뜻이다.
const ORDER_DETAIL: OrderDetail = {
  orderNo: 'ORD-1',
  status: 'PAID',
  totalAmount: 60000,
  discountAmount: 2000,
  payableAmount: 58000,
  receiverName: '박철수',
  receiverPhone: '01099998888',
  zipcode: '13494',
  address1: '경기도 성남시 분당구 판교역로 1',
  address2: '3층',
  deliveryType: 'NORMAL',
  orderedAt: '2026-07-20T10:00:00',
  paidAt: '2026-07-20T10:01:00',
  items: [
    {
      goodsName: '그린티 토너',
      optionName: '기본',
      unitPrice: 20000,
      quantity: 2,
      lineAmount: 40000,
    },
    {
      goodsName: '저자극 클렌저',
      optionName: '',
      unitPrice: 18000,
      quantity: 1,
      lineAmount: 18000,
    },
  ],
};

function registerHandlers(options: { orders?: OrderSummary[] } = {}) {
  const { orders = [ORDER_SUMMARY] } = options;
  server.use(
    http.get('/api/v1/orders', () => HttpResponse.json(envelope(orders))),
    http.get('/api/v1/orders/:orderNo', () => HttpResponse.json(envelope(ORDER_DETAIL))),
    // 현재 회원 배송지 — 주문 상세가 이걸 참조하면 안 된다(위 주석 참고).
    http.get('/api/v1/members/me/addresses', () =>
      HttpResponse.json(envelope([CURRENT_MEMBER_ADDRESS])),
    ),
  );
}

function renderMyOrders(initialPath = '/mypage/orders') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/mypage/orders" element={<MyOrders />} />
          <Route path="/mypage/orders/:orderNo" element={<MyOrders />} />
        </Routes>
      </MemoryRouter>
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

describe('MyOrders — 마이페이지 주문내역', () => {
  it('주문 목록은 "대표상품 외 N건" 형태로 보여준다', async () => {
    registerHandlers();

    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/mypage/orders']}>
          <Routes>
            <Route path="/mypage/orders" element={<MyOrders />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(await screen.findByText('그린티 토너 외 2건')).toBeInTheDocument();
  });

  it('주문이 없으면 EmptyState를 보여준다', async () => {
    registerHandlers({ orders: [] });

    renderMyOrders();

    const status = await screen.findByRole('status');
    expect(status).toHaveTextContent('아직 주문 내역이 없어요');
  });

  it('주문을 누르면 상세로 이동해 스냅샷 배송지와 금액을 보여준다', async () => {
    registerHandlers();

    renderMyOrders();

    fireEvent.click(await screen.findByText('그린티 토너 외 2건'));

    // 주문 시점 스냅샷(ORD-1 상세의 박철수·성남시)이 보여야 한다 — 현재 회원 배송지(김민수·강남구)가 아니다.
    await waitFor(() => expect(screen.getByText('박철수')).toBeInTheDocument());
    expect(screen.getByText(/성남시 분당구/)).toBeInTheDocument();
    expect(screen.queryByText('김민수')).not.toBeInTheDocument();
    expect(screen.queryByText(/강남구/)).not.toBeInTheDocument();
    // 금액도 서버 응답(payableAmount)을 그대로 보여준다 — 프론트가 다시 계산하지 않는다.
    expect(screen.getByText('58,000원')).toBeInTheDocument();
  });
});

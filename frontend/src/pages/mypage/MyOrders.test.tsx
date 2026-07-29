import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation, type Location } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { MyOrders } from './MyOrders';
import { ToastProvider } from '../../components/ui/ToastProvider';
import type { Address } from '../../api/member';
import type { OrderDetail, OrderSummary } from '../../api/order';

function envelope<T>(data: T) {
  return { code: 'OK', message: 'success', data };
}

/** 목 서버가 마지막으로 받은 /orders 요청의 searchParams — 배선 테스트의 관측 지점(GoodsList와 동일). */
let capturedSearchParams: URLSearchParams | null = null;
/** MemoryRouter 내부의 현재 위치 — "URL이 상태의 진실" 단언용. */
let location: Location | null = null;

function LocationProbe() {
  location = useLocation();
  return null;
}

function currentLocation(): Location {
  if (!location) throw new Error('LocationProbe가 아직 렌더되지 않았다');
  return location;
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
      orderItemId: 11,
      goodsName: '그린티 토너',
      optionName: '기본',
      unitPrice: 20000,
      quantity: 2,
      lineAmount: 40000,
      canceledQuantity: 0,
    },
    {
      orderItemId: 12,
      goodsName: '저자극 클렌저',
      optionName: '',
      unitPrice: 18000,
      quantity: 1,
      lineAmount: 18000,
      canceledQuantity: 0,
    },
  ],
  refundedAmount: 0,
  cancels: [],
};

function registerHandlers(options: { orders?: OrderSummary[]; totalPages?: number } = {}) {
  const { orders = [ORDER_SUMMARY], totalPages = 1 } = options;
  server.use(
    http.get('/api/v1/orders', ({ request }) => {
      capturedSearchParams = new URL(request.url).searchParams;
      return HttpResponse.json(
        envelope({
          content: orders,
          page: 0,
          size: 10,
          totalElements: orders.length,
          totalPages,
          hasNext: totalPages > 1,
        }),
      );
    }),
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
      {/* 취소 모달이 토스트를 쓴다 — 실제 앱과 같이 프로바이더 안에서 렌더한다. */}
      <ToastProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/mypage/orders" element={<MyOrders />} />
            <Route path="/mypage/orders/:orderNo" element={<MyOrders />} />
          </Routes>
          <LocationProbe />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  capturedSearchParams = null;
  location = null;
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
    // 재주문 등으로 날짜·상품명·금액이 같은 건이 쌓여도 주문번호로 구분할 수 있어야 한다.
    expect(await screen.findByText(/ORD-1/)).toBeInTheDocument();
  });

  it('주문이 없으면 EmptyState를 보여준다', async () => {
    registerHandlers({ orders: [] });

    renderMyOrders();

    // 토스트 라이브리전도 role=status로 상시 존재한다 — 역할로 먼저 찾으면 그쪽이 잡힌다.
    // 빈 상태 문구를 기다린 뒤, 그것이 role=status 안에서 읽히는지를 확인한다.
    const empty = await screen.findByText('아직 주문 내역이 없어요');
    expect(empty.closest('[role="status"]')).not.toBeNull();
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

  it('주문이 두 페이지 이상이면 페이저가 보인다', async () => {
    registerHandlers({ totalPages: 3 });

    renderMyOrders();

    expect(await screen.findByRole('navigation', { name: '페이지 이동' })).toBeInTheDocument();
  });

  it('한 페이지뿐이면 페이저를 렌더하지 않는다', async () => {
    registerHandlers({ totalPages: 1 });

    renderMyOrders();

    await screen.findByText('그린티 토너 외 2건');
    expect(screen.queryByRole('navigation', { name: '페이지 이동' })).not.toBeInTheDocument();
  });

  it('2페이지를 누르면 URL이 ?page=2가 되고 서버에 page=1을 보낸다 (URL은 1-based, API는 0-based)', async () => {
    registerHandlers({ totalPages: 3 });

    renderMyOrders();

    fireEvent.click(await screen.findByRole('button', { name: '2' }));

    expect(currentLocation().search).toContain('page=2');
    await waitFor(() => {
      expect(capturedSearchParams?.get('page')).toBe('1');
    });
  });

  it('1페이지로 돌아가면 page 파라미터를 URL에서 지운다', async () => {
    registerHandlers({ totalPages: 3 });

    renderMyOrders('/mypage/orders?page=2');

    fireEvent.click(await screen.findByRole('button', { name: '1' }));

    expect(currentLocation().search).not.toContain('page=');
  });

  it('배지는 상태별 라벨을 렌더한다 — 색이 아니라 글자가 상태를 알린다', async () => {
    registerHandlers({
      orders: [
        { ...ORDER_SUMMARY, orderNo: 'ORD-P', status: 'PENDING' },
        { ...ORDER_SUMMARY, orderNo: 'ORD-1', status: 'PAID' },
        { ...ORDER_SUMMARY, orderNo: 'ORD-2', status: 'PARTIALLY_CANCELED' },
        { ...ORDER_SUMMARY, orderNo: 'ORD-3', status: 'CANCELED' },
      ],
    });

    renderMyOrders();

    expect(await screen.findByText('결제대기')).toBeInTheDocument();
    expect(screen.getByText('결제완료')).toBeInTheDocument();
    expect(screen.getByText('부분취소')).toBeInTheDocument();
    expect(screen.getByText('취소완료')).toBeInTheDocument();
  });

  it('취소 버튼은 결제완료와 부분취소 주문에만 보인다', async () => {
    registerHandlers({
      orders: [
        { ...ORDER_SUMMARY, orderNo: 'ORD-1', status: 'PAID' },
        { ...ORDER_SUMMARY, orderNo: 'ORD-2', status: 'PARTIALLY_CANCELED' },
        { ...ORDER_SUMMARY, orderNo: 'ORD-3', status: 'CANCELED' },
        { ...ORDER_SUMMARY, orderNo: 'ORD-P', status: 'PENDING' },
      ],
    });

    renderMyOrders();

    await screen.findByText('결제완료');
    expect(screen.getAllByRole('button', { name: '주문 취소' })).toHaveLength(2);
  });

  it('취소된 주문 상세는 회차 이력과 환불 합계를 보여준다', async () => {
    const canceled: OrderDetail = {
      ...ORDER_DETAIL,
      status: 'PARTIALLY_CANCELED',
      items: [
        { ...ORDER_DETAIL.items[0], canceledQuantity: 1 },
        ORDER_DETAIL.items[1],
      ],
      refundedAmount: 20000,
      cancels: [
        { refundAmount: 20000, reason: '단순 변심', canceledAt: '2026-07-25T13:40:00' },
      ],
    };
    server.use(
      http.get('/api/v1/orders', () =>
        HttpResponse.json(
          envelope({
            content: [ORDER_SUMMARY],
            page: 0,
            size: 10,
            totalElements: 1,
            totalPages: 1,
            hasNext: false,
          }),
        ),
      ),
      http.get('/api/v1/orders/:orderNo', () => HttpResponse.json(envelope(canceled))),
    );

    renderMyOrders('/mypage/orders/ORD-1');

    expect(await screen.findByText('취소 내역')).toBeInTheDocument();
    expect(screen.getByText(/2026-07-25/)).toBeInTheDocument();
    expect(screen.getByText(/단순 변심/)).toBeInTheDocument();
    // 회차별 환불액(−20,000원)과 합계(20,000원)가 둘 다 읽혀야 한다.
    expect(screen.getByText('−20,000원')).toBeInTheDocument();
    expect(screen.getByText('환불 합계')).toBeInTheDocument();
    // 항목에도 몇 개가 취소됐는지 남는다.
    expect(screen.getByText('1개 취소')).toBeInTheDocument();
  });

  it('취소 이력이 없는 주문에는 취소 내역 절이 없다', async () => {
    registerHandlers();

    renderMyOrders('/mypage/orders/ORD-1');

    await screen.findByText('박철수');
    expect(screen.queryByText('취소 내역')).not.toBeInTheDocument();
  });

  it('취소 버튼을 누르면 취소 모달이 열린다 — 행 이동은 일어나지 않는다', async () => {
    registerHandlers();

    renderMyOrders();

    fireEvent.click(await screen.findByRole('button', { name: '주문 취소' }));

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    // 취소 버튼은 행 클릭(상세 이동)을 삼켜야 한다.
    expect(currentLocation().pathname).toBe('/mypage/orders');
  });
});

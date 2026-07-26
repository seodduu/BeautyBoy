import { StrictMode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { ToastProvider } from '../components/ui/ToastProvider';
import { OrderComplete } from './OrderComplete';
import * as orderApi from '../api/order';
import { useAuthStore } from '../stores/authStore';

function envelope<T>(data: T) {
  return { code: 'OK', message: '성공', data };
}

let confirmPaymentSpy: ReturnType<typeof vi.spyOn>;

/**
 * StrictMode로 감싸 렌더한다 — 개발 모드 이중 마운트에서도 승인이 한 번만 나가는지가
 * 이 화면의 핵심 계약이라, 테스트 환경에서 그 조건을 실제로 재현한다.
 */
function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });

  return render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <MemoryRouter initialEntries={[path]}>
            <Routes>
              <Route path="/order/complete" element={<OrderComplete />} />
            </Routes>
          </MemoryRouter>
        </ToastProvider>
      </QueryClientProvider>
    </StrictMode>,
  );
}

beforeEach(() => {
  // ToastProvider가 prefers-reduced-motion 판정에 matchMedia를 쓰므로 jsdom에 최소 구현을 채운다.
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

  // 실제 HTTP 경로(axios + msw)를 그대로 타면서 호출 횟수·인자만 관찰한다.
  confirmPaymentSpy = vi.spyOn(orderApi, 'confirmPayment');
});

afterEach(() => {
  confirmPaymentSpy.mockRestore();
});

describe('OrderComplete — 결제 완료', () => {
  it('쿼리의 paymentKey·orderId·amount로 승인을 한 번만 요청한다', async () => {
    server.use(
      http.post('/api/v1/payments/confirm', () =>
        HttpResponse.json(envelope({ orderNo: 'ORD-1', status: 'PAID', paidAmount: 43000 })),
      ),
    );

    renderAt('/order/complete?paymentKey=pk_1&orderId=ORD-1&amount=43000');
    await screen.findByText(/주문이 완료/);
    expect(confirmPaymentSpy).toHaveBeenCalledTimes(1);
    expect(confirmPaymentSpy).toHaveBeenCalledWith('ORD-1', 'pk_1', 43000);
  });

  it('승인 성공 시 주문번호와 결제 금액을 보여준다', async () => {
    server.use(
      http.post('/api/v1/payments/confirm', () =>
        HttpResponse.json(envelope({ orderNo: 'ORD-1', status: 'PAID', paidAmount: 43000 })),
      ),
    );

    renderAt('/order/complete?paymentKey=pk_1&orderId=ORD-1&amount=43000');
    expect(await screen.findByText('ORD-1')).toBeInTheDocument();
    expect(screen.getByText(/43,000/)).toBeInTheDocument();
  });

  it('금액 불일치로 승인이 실패하면 실패 안내를 보여주고 완료로 오인시키지 않는다', async () => {
    // confirmPayment가 PAYMENT_AMOUNT_MISMATCH로 reject
    server.use(
      http.post('/api/v1/payments/confirm', () =>
        HttpResponse.json(
          {
            code: 'PAYMENT_AMOUNT_MISMATCH',
            message: '결제 금액이 주문 금액과 일치하지 않습니다',
            detail: null,
          },
          { status: 400 },
        ),
      ),
    );

    renderAt('/order/complete?paymentKey=pk_1&orderId=ORD-1&amount=1');
    expect(await screen.findByRole('alert')).toHaveTextContent(/일치하지 않/);
    expect(screen.queryByText(/주문이 완료/)).not.toBeInTheDocument();
  });

  it('쿼리 파라미터가 없으면 승인을 부르지 않고 잘못된 접근으로 안내한다', async () => {
    renderAt('/order/complete');
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(confirmPaymentSpy).not.toHaveBeenCalled();
  });
});

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { ToastProvider } from '../../components/ui/ToastProvider';
import { CancelOrderModal } from './CancelOrderModal';
import type { OrderDetail } from '../../api/order';

function envelope<T>(data: T) {
  return { code: 'OK', message: 'success', data };
}

/**
 * 토너 2개(1개 이미 취소 → 잔여 1) · 클렌저 1개(잔여 1) · 크림 1개(전량 취소 → 잔여 0).
 * 잔여가 0/1/1로 갈리는 픽스처 하나로 "잔여 0은 못 고른다"와 "상한은 잔여다"를 함께 잰다.
 */
const DETAIL: OrderDetail = {
  orderNo: 'ORD-1',
  status: 'PARTIALLY_CANCELED',
  totalAmount: 78000,
  discountAmount: 0,
  payableAmount: 78000,
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
      unitPrice: 24100,
      quantity: 2,
      lineAmount: 48200,
      canceledQuantity: 1,
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
    {
      orderItemId: 13,
      goodsName: '수분 크림',
      optionName: '',
      unitPrice: 11800,
      quantity: 1,
      lineAmount: 11800,
      canceledQuantity: 1,
    },
  ],
  refundedAmount: 35900,
  cancels: [{ refundAmount: 35900, reason: '단순 변심', canceledAt: '2026-07-21T09:00:00' }],
};

/** 목이 마지막으로 받은 취소 요청 바디 — "무엇을 보냈나"의 관측 지점. */
let capturedCancelBody: unknown = null;

function registerHandlers(cancel?: () => Response) {
  server.use(
    http.get('/api/v1/orders/:orderNo', () => HttpResponse.json(envelope(DETAIL))),
    http.post('/api/v1/orders/:orderNo/cancel', async ({ request }) => {
      capturedCancelBody = await request.json();
      if (cancel) return cancel();
      return HttpResponse.json(
        envelope({
          orderNo: 'ORD-1',
          status: 'CANCELED',
          refundAmount: 24100,
          canceledAt: '2026-07-30T12:00:00',
        }),
      );
    }),
  );
}

function renderModal(onClose = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  const view = render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <CancelOrderModal open orderNo="ORD-1" onClose={onClose} />
      </ToastProvider>
    </QueryClientProvider>,
  );
  return { ...view, onClose, invalidateSpy };
}

/** 항목 체크박스 — 접근 가능한 이름은 상품명이다. */
function itemCheckbox(name: string) {
  return screen.getByRole('checkbox', { name: new RegExp(name) });
}

beforeEach(() => {
  capturedCancelBody = null;
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

describe('CancelOrderModal — 수량 선택 취소 모달', () => {
  it('잔여 수량이 0인 항목은 선택할 수 없다', async () => {
    registerHandlers();

    renderModal();

    // 잔여 0인 크림은 비활성, 잔여가 남은 두 항목은 선택 가능하다.
    await waitFor(() => expect(itemCheckbox('수분 크림')).toBeDisabled());
    expect(itemCheckbox('그린티 토너')).toBeEnabled();
    expect(itemCheckbox('저자극 클렌저')).toBeEnabled();
  });

  it('잔여 수량을 넘는 수량은 선택할 수 없다', async () => {
    registerHandlers();

    renderModal();

    // 토너는 2개 중 1개가 이미 취소돼 잔여 1 — 늘리기 버튼이 처음부터 막혀 있어야 한다.
    fireEvent.click(await screen.findByRole('checkbox', { name: /그린티 토너/ }));

    expect(screen.getByRole('button', { name: '수량 늘리기' })).toBeDisabled();
    // 잔여가 1이면 하한(1)과 상한(1)이 같아 양쪽이 다 막힌다.
    expect(screen.getByRole('button', { name: '수량 줄이기' })).toBeDisabled();
  });

  it('선택 항목이 없으면 확정 버튼이 비활성이다', async () => {
    registerHandlers();

    renderModal();

    const submit = await screen.findByRole('button', { name: '취소 확정' });
    expect(submit).toBeDisabled();

    fireEvent.click(itemCheckbox('저자극 클렌저'));
    expect(submit).toBeEnabled();

    // 선택을 다시 풀면 원래대로 막힌다.
    fireEvent.click(itemCheckbox('저자극 클렌저'));
    expect(submit).toBeDisabled();
  });

  it('예상 환불액은 선택 수량 × 스냅샷 단가다', async () => {
    registerHandlers();

    renderModal();

    fireEvent.click(await screen.findByRole('checkbox', { name: /그린티 토너/ }));
    expect(screen.getByTestId('estimated-refund')).toHaveTextContent('24,100원');

    fireEvent.click(itemCheckbox('저자극 클렌저'));
    expect(screen.getByTestId('estimated-refund')).toHaveTextContent('42,100원');
  });

  it('확정하면 선택 항목과 사유를 보내고, 성공 시 토스트·쿼리 무효화·닫기가 일어난다', async () => {
    registerHandlers();

    const { onClose, invalidateSpy } = renderModal();

    fireEvent.click(await screen.findByRole('checkbox', { name: /저자극 클렌저/ }));
    fireEvent.change(screen.getByLabelText('취소 사유'), { target: { value: '주문 실수' } });
    fireEvent.click(screen.getByRole('button', { name: '취소 확정' }));

    await waitFor(() =>
      expect(capturedCancelBody).toEqual({
        items: [{ orderItemId: 12, quantity: 1 }],
        reason: '주문 실수',
      }),
    );
    // 환불액은 서버 응답값을 그대로 읽어준다 — 화면이 계산한 예상액이 아니다.
    expect(await screen.findByText(/환불 24,100원/)).toBeInTheDocument();
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(invalidateSpy).toHaveBeenCalled();
  });

  it('502(결제사 통신 실패)면 모달이 유지된다 — 롤백됐으므로 재시도가 안전하다', async () => {
    registerHandlers(() =>
      HttpResponse.json(
        { code: 'PAYMENT_CANCEL_FAILED', message: '결제 취소에 실패했습니다', data: null },
        { status: 502 },
      ),
    );

    const { onClose } = renderModal();

    fireEvent.click(await screen.findByRole('checkbox', { name: /저자극 클렌저/ }));
    fireEvent.click(screen.getByRole('button', { name: '취소 확정' }));

    expect(await screen.findByText(/결제사 통신에 실패했어요/)).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });

  it('409면 이미 처리됐다고 알리고 닫는다 — 화면이 들고 있던 잔여 수량이 낡았다는 뜻이다', async () => {
    registerHandlers(() =>
      HttpResponse.json(
        { code: 'ORDER_CANCEL_QUANTITY_EXCEEDED', message: '초과', data: null },
        { status: 409 },
      ),
    );

    const { onClose } = renderModal();

    fireEvent.click(await screen.findByRole('checkbox', { name: /저자극 클렌저/ }));
    fireEvent.click(screen.getByRole('button', { name: '취소 확정' }));

    expect(await screen.findByText(/이미 처리된 주문이에요/)).toBeInTheDocument();
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it('Esc로 닫힌다', async () => {
    registerHandlers();

    const { onClose } = renderModal();

    await screen.findByRole('dialog');
    fireEvent.keyDown(document, { key: 'Escape' });

    expect(onClose).toHaveBeenCalled();
  });

  it('사유로 기타를 고르면 직접 입력 칸이 나오고 200자로 제한된다', async () => {
    registerHandlers();

    renderModal();

    fireEvent.change(await screen.findByLabelText('취소 사유'), { target: { value: '기타' } });

    const detail = screen.getByLabelText('사유 직접 입력');
    expect(detail).toHaveAttribute('maxLength', '200');
  });
});

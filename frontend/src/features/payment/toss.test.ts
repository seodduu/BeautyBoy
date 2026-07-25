import { beforeEach, describe, expect, it, vi } from 'vitest';

const requestPaymentMock = vi.fn().mockResolvedValue(undefined);
const paymentMock = vi.fn().mockReturnValue({ requestPayment: requestPaymentMock });
const loadTossPaymentsMock = vi.fn().mockResolvedValue({ payment: paymentMock });

vi.mock('@tosspayments/tosspayments-sdk', () => ({
  loadTossPayments: (...args: unknown[]) => loadTossPaymentsMock(...args),
}));

const { requestTossPayment } = await import('./toss');

describe('requestTossPayment', () => {
  beforeEach(() => {
    requestPaymentMock.mockClear();
    paymentMock.mockClear();
    loadTossPaymentsMock.mockClear();
  });

  it('customerKey로 결제창을 열고 서버가 준 amount·orderNo를 그대로 넘긴다', async () => {
    await requestTossPayment({
      orderNo: 'ORD-1',
      orderName: '그린티 토너 외 1건',
      amount: 43000,
      customerKey: 'bb-1',
    });

    expect(paymentMock).toHaveBeenCalledWith({ customerKey: 'bb-1' });
    expect(requestPaymentMock).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'CARD',
        orderId: 'ORD-1',
        orderName: '그린티 토너 외 1건',
        amount: { currency: 'KRW', value: 43000 },
        successUrl: expect.stringContaining('/order/complete'),
        failUrl: expect.stringContaining('/order/fail'),
      }),
    );
  });

  it('결제창을 열기 전에 loadTossPayments를 호출한다', async () => {
    await requestTossPayment({
      orderNo: 'ORD-2',
      orderName: '저자극 클렌저',
      amount: 3000,
      customerKey: 'bb-2',
    });

    expect(loadTossPaymentsMock).toHaveBeenCalledTimes(1);
  });
});

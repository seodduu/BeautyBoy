import { loadTossPayments } from '@tosspayments/tosspayments-sdk';

// 클라이언트 키는 공개 값이지만 환경마다 다르므로 코드에 박지 않는다(CLAUDE.md 시크릿 규칙의 연장).
const CLIENT_KEY = import.meta.env.VITE_TOSS_CLIENT_KEY;

export interface RequestTossPaymentParams {
  orderNo: string;
  orderName: string;
  amount: number;
  customerKey: string;
}

/**
 * 토스 결제창을 연다. 성공하면 토스가 successUrl로 리다이렉트하므로 이 함수는 정상 경로에서
 * 반환하지 않는다 — 반환하는 경우는 사용자가 결제창을 닫는 등 실패 경로뿐이다.
 *
 * amount는 항상 호출부(주문서 화면)가 `POST /orders` 응답으로 받은 payableAmount를 그대로
 * 넘긴다 — 프론트가 화면 합계를 다시 계산해 넘기지 않는다(project law: 돈은 서버).
 */
export async function requestTossPayment({
  orderNo,
  orderName,
  amount,
  customerKey,
}: RequestTossPaymentParams): Promise<void> {
  const tossPayments = await loadTossPayments(CLIENT_KEY);
  const payment = tossPayments.payment({ customerKey });
  await payment.requestPayment({
    method: 'CARD',
    amount: { currency: 'KRW', value: amount },
    orderId: orderNo,
    orderName,
    successUrl: `${window.location.origin}/order/complete`,
    failUrl: `${window.location.origin}/order/fail`,
    card: { useEscrow: false, flowMode: 'DEFAULT', useCardPoint: false, useAppCardOnly: false },
  });
}

import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { confirmPayment, type PaymentConfirmResult } from '../api/order';
import { queryKeys } from '../api/queryKeys';
import { formatWon } from '../components/ui/Price';
import { useTitle } from '../hooks/useTitle';
import './OrderComplete.css';

/** 서버 에러 봉투(`{ code, message, detail }`)에서 코드·문구를 꺼낸다. 문구는 서버가 준 것을 그대로 쓴다. */
function readServerError(error: unknown): { code: string | null; message: string | null } {
  if (isAxiosError(error) && error.response?.data) {
    const data = error.response.data as { code?: string; message?: string };
    return { code: data.code ?? null, message: data.message ?? null };
  }
  return { code: null, message: null };
}

/**
 * 결제 완료 `/order/complete` — 토스 successUrl. 토스가
 * `?paymentKey=…&orderId=…&amount=…`로 리다이렉트한다(쿼리 이름은 토스가 정한 계약이라 바꿀 수 없다).
 *
 * 이 화면은 금액을 계산하지 않는다. 쿼리의 amount는 토스가 준 값 그대로 승인 요청에 실어 보내고
 * (서버가 주문의 payableAmount와 대조해 판정한다), 화면에 확정 금액으로 표시하는 값은 승인 응답의
 * `paidAmount`다 (project law: 돈은 서버).
 */
export function OrderComplete() {
  useTitle('주문 완료');
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();

  const paymentKey = searchParams.get('paymentKey');
  const orderId = searchParams.get('orderId');
  const amountParam = searchParams.get('amount');
  const amount = Number(amountParam);
  const hasValidParams =
    Boolean(paymentKey && orderId && amountParam) && Number.isFinite(amount) && amount > 0;

  const { mutateAsync } = useMutation({
    mutationFn: () => confirmPayment(orderId as string, paymentKey as string, amount),
  });

  // 승인 결과를 컴포넌트 state로 들고 있는 이유:
  // useMutation의 isPending/isSuccess는 옵저버(구독)에 붙어 있어서, StrictMode 이중 마운트에서
  // 첫 마운트가 언마운트될 때 초기화된다. 요청은 ref 가드 때문에 다시 나가지 않으므로 재마운트된
  // 화면은 결과를 영영 못 받아 "승인 중"에서 멈춘다(실제로 재현했다). React state는 StrictMode
  // 재마운트에서도 보존되므로, mutateAsync가 돌려준 결과를 여기에 적어두면 표시가 유실되지 않는다.
  const [result, setResult] = useState<PaymentConfirmResult | null>(null);
  const [failure, setFailure] = useState<{ code: string | null; message: string | null } | null>(null);

  // 승인은 정확히 한 번만 나가야 한다. StrictMode 개발 모드는 마운트 이펙트를 두 번 실행하므로
  // ref 가드 없이는 두 번째 요청이 PAYMENT_ALREADY_CONFIRMED로 실패해 "성공한 결제"를 실패로
  // 표시하게 된다. ref는 StrictMode 재마운트에서도 유지되므로 이 가드가 유효하다.
  const requestedRef = useRef(false);
  useEffect(() => {
    if (!hasValidParams || requestedRef.current) {
      return;
    }
    requestedRef.current = true;
    mutateAsync()
      .then((confirmed) => {
        setResult(confirmed);
        // 주문이 끝나 장바구니가 비었으므로 다시 읽는다.
        queryClient.invalidateQueries({ queryKey: queryKeys.cart() });
      })
      .catch((err: unknown) => setFailure(readServerError(err)));
  }, [hasValidParams, mutateAsync, queryClient]);

  if (!hasValidParams) {
    return (
      <div className="bb-order-complete">
        <h1 className="bb-order-complete__title">잘못된 접근입니다</h1>
        <p className="bb-order-complete__alert" role="alert">
          결제 정보가 없어 승인을 진행할 수 없습니다. 결제는 주문서에서 다시 시작해 주세요.
        </p>
        <div className="bb-order-complete__actions">
          <Link className="bb-order-complete__link" to="/cart">
            장바구니로 가기
          </Link>
        </div>
      </div>
    );
  }

  if (failure) {
    const { code, message } = failure;

    // PAYMENT_ALREADY_CONFIRMED는 "실패"로 보여주지 않는다 — 이 코드가 오는 상황은 결제가 이미
    // 승인된(=돈이 빠져나간) 경우뿐이고, 완료 화면 새로고침만으로도 재현된다. 실패로 표기하면
    // 사용자가 결제가 안 된 줄 알고 다시 결제할 수 있어 피해가 크다. 반대로 이 화면은 승인 응답을
    // 받지 못했으므로 확정 금액(paidAmount)을 알 수 없다 — 금액을 추측해 적지 않고(돈은 서버),
    // 주문번호와 서버 문구만 그대로 보여준다.
    if (code === 'PAYMENT_ALREADY_CONFIRMED') {
      return (
        <div className="bb-order-complete">
          <h1 className="bb-order-complete__title">이미 처리된 결제입니다</h1>
          <p className="bb-order-complete__status" role="status">
            {message}
          </p>
          <dl className="bb-order-complete__facts">
            <dt>주문번호</dt>
            <dd className="bb-order-complete__order-no">{orderId}</dd>
          </dl>
          <div className="bb-order-complete__actions">
            <Link className="bb-order-complete__link" to="/main">
              쇼핑 계속하기
            </Link>
          </div>
        </div>
      );
    }

    return (
      <div className="bb-order-complete">
        <h1 className="bb-order-complete__title">결제를 승인하지 못했습니다</h1>
        {/* 서버가 준 문구를 그대로 보여준다 — 프론트가 실패 사유를 새로 지어내지 않는다. */}
        <p className="bb-order-complete__alert" role="alert">
          {message ?? '결제 승인 중 문제가 발생했습니다. 결제가 되었는지 주문 내역에서 확인해 주세요.'}
        </p>
        <div className="bb-order-complete__actions">
          <Link className="bb-order-complete__link" to="/cart">
            장바구니로 가기
          </Link>
        </div>
      </div>
    );
  }

  if (!result) {
    return (
      <div className="bb-order-complete">
        <h1 className="bb-order-complete__title">결제를 승인하는 중입니다</h1>
        <p className="bb-order-complete__status" role="status">
          창을 닫지 말고 잠시만 기다려 주세요.
        </p>
      </div>
    );
  }

  return (
    <div className="bb-order-complete">
      <h1 className="bb-order-complete__title">주문이 완료되었습니다</h1>
      <p className="bb-order-complete__lead">결제가 정상적으로 승인되었어요. 배송 준비가 시작됩니다.</p>
      <dl className="bb-order-complete__facts">
        <dt>주문번호</dt>
        <dd className="bb-order-complete__order-no">{result.orderNo}</dd>
        <dt>결제 금액</dt>
        <dd className="bb-order-complete__amount">{formatWon(result.paidAmount)}</dd>
      </dl>
      <div className="bb-order-complete__actions">
        <Link className="bb-order-complete__link" to="/main">
          쇼핑 계속하기
        </Link>
      </div>
    </div>
  );
}

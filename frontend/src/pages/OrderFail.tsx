import { Link, useSearchParams } from 'react-router-dom';
import './OrderFail.css';

/**
 * 결제 실패 `/order/fail` — 토스 failUrl. 토스가 `?code=…&message=…&orderId=…`로 리다이렉트한다
 * (쿼리 이름은 토스가 정한 계약이라 바꿀 수 없다).
 *
 * 여기서는 승인 요청을 보내지 않는다 — 결제가 성립하지 않았으므로 승인할 것이 없다.
 * 실패 사유는 토스가 준 message를 그대로 보여준다(프론트가 문구를 새로 지어내지 않는다).
 */
export function OrderFail() {
  const [searchParams] = useSearchParams();

  const code = searchParams.get('code');
  const message = searchParams.get('message');
  const orderId = searchParams.get('orderId');

  return (
    <div className="bb-order-fail">
      <h1 className="bb-order-fail__title">결제를 완료하지 못했습니다</h1>
      <p className="bb-order-fail__alert" role="alert">
        {message ?? '결제가 취소되었거나 처리 중 문제가 발생했습니다.'}
      </p>

      {(orderId || code) && (
        <dl className="bb-order-fail__facts">
          {orderId && (
            <>
              <dt>주문번호</dt>
              <dd>{orderId}</dd>
            </>
          )}
          {code && (
            <>
              <dt>실패 코드</dt>
              <dd>{code}</dd>
            </>
          )}
        </dl>
      )}

      <p className="bb-order-fail__note">
        결제가 승인되지 않았으므로 금액은 청구되지 않습니다. 장바구니는 그대로 남아 있어요.
      </p>

      <div className="bb-order-fail__actions">
        <Link className="bb-order-fail__link" to="/order">
          다시 결제하기
        </Link>
        <Link className="bb-order-fail__link" to="/cart">
          장바구니로 가기
        </Link>
      </div>
    </div>
  );
}

// tools/loadtest/confirm.js — 시나리오 ①: 주문 생성→확정. before/after에서 동일하게 사용.
//
// 엔드포인트/필드명은 backend/src/main/java/com/beautyboy/{auth,order,payment} 컨트롤러·dto 실물 기준
// (모두 /api/v1 접두사, 응답은 ApiResponse{code,message,data}로 감싸여 온다):
//   - POST /api/v1/auth/login          req {email,password}           → data.accessToken
//   - POST /api/v1/orders              req {items:[{goodsNo,optionNo,quantity}], receiverName,
//                                            receiverPhone, zipcode, address1, address2, deliveryType}
//                                       → 201, data.{orderNo, payableAmount}
//     검증 레이어가 필드마다 다르다 — receiverName/receiverPhone/zipcode/address1/deliveryType은
//     OrderCreateRequest에 @NotBlank(Bean Validation, 400)가 붙어 있고, items/goodsNo/optionNo/quantity는
//     어노테이션이 없다(@Valid만). 이 셋은 OrderService가 서비스 레이어에서 판정한다
//     (CART_EMPTY, CART_QUANTITY_INVALID, GOODS_NOT_FOUND, ORDER_OUT_OF_STOCK).
//   - POST /api/v1/payments/confirm    req {orderNo, paymentKey, amount} → 200, data.{orderNo,status,paidAmount}
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

// 부하 모형 숫자는 계약이다 — before/after 비교가 깨지므로 절대 바꾸지 않는다.
export const options = {
  scenarios: {
    confirm: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '30s', target: 50 },   // 워밍업
        { duration: '1m',  target: 200 },  // 본 측정 구간 — 200VU는 로컬 커넥션 풀 한계 직전
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: { http_req_failed: ['rate<0.01'] }, // 에러율 1% 초과면 측정 자체가 무효
};

export function setup() {
  const login = http.post(`${BASE}/api/v1/auth/login`, JSON.stringify({
    email: __ENV.LOADTEST_EMAIL, password: __ENV.LOADTEST_PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' } });
  if (login.status !== 200) {
    throw new Error(`setup 로그인 실패: status=${login.status} body=${login.body}`);
  }
  return { token: login.json('data.accessToken') };
}

export default function (data) {
  const auth = { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` } };

  // 매 반복 주문 생성 → 확정. goodsNo/optionNo는 시드의 재고 넉넉한 상품으로 고정(env로 주입,
  // README 참고 — 재고는 결제 확정 시 차감되므로 반복 횟수만큼 미리 채워둬야 한다).
  const order = http.post(`${BASE}/api/v1/orders`, JSON.stringify({
    items: [{ goodsNo: Number(__ENV.GOODS_ID), optionNo: Number(__ENV.OPTION_ID), quantity: 1 }],
    receiverName: 'k6 부하테스트',
    receiverPhone: '010-0000-0000',
    zipcode: '06134',
    address1: '서울특별시 강남구 테헤란로 123',
    address2: 'k6',
    deliveryType: 'NORMAL',
  }), auth);
  if (!check(order, { 'order created': (r) => r.status === 201 })) return;

  const orderNo = order.json('data.orderNo');
  const payableAmount = order.json('data.payableAmount');

  const confirm = http.post(`${BASE}/api/v1/payments/confirm`, JSON.stringify({
    orderNo, paymentKey: `stub-${orderNo}`, amount: payableAmount,
  }), auth);
  check(confirm, { 'confirmed': (r) => r.status === 200 });
}

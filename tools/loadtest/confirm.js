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

// LOAD_MODEL 스위치 (Task 0.4) — 부하 모형 계약(ramping-vus 단계/threshold/요청 순서)은
// 그대로 두고 "어떤 상품을 사는가"만 바꾼다. 기본값 'single'은 기존 동작(GOODS_ID/OPTION_ID
// 고정)과 정확히 동일해야 한다 — 기존 baseline(2026-07-29-baseline)을 그대로 재현할 수 있어야
// 하기 때문이다.
//
// spread 모드에서 goodsNo/optionNo 쌍을 어떻게 넘길지: 주문 생성 API가 items에
// {goodsNo, optionNo}를 쌍으로 요구하므로(OrderCreateRequest), optionNo만 바꾸고 goodsNo를
// 고정하면 요청이 깨진다(옵션이 다른 상품 소속이면 GOODS_NOT_FOUND/불일치). 200개 이상의 쌍을
// 쉼표구분 환경변수로 넘기면 한 줄이 지나치게 길어지므로, k6의 open()으로 JSON 파일을 읽는
// 방식을 택했다 — README에 파일을 만드는 SQL을 적어 뒀다.
const MODEL = __ENV.LOAD_MODEL || 'single';
const PAIRS = MODEL === 'spread'
  ? JSON.parse(open(__ENV.PAIRS_FILE || './pairs.json'))
  : null;

// __VU와 __ITER를 함께 쓰는 이유: __VU만 쓰면 같은 VU가 매 반복 같은 쌍을 골라
// 쌍 개수가 VU 수보다 적을 때만이 아니라도(VU 수와 쌍 수가 같아도) 반복마다 재고 차감이
// 항상 같은 옵션에 몰리는 문제가 생긴다. (__VU + __ITER) % length로 반복마다 회전시킨다.
function pickItem() {
  if (MODEL !== 'spread') {
    return { goodsNo: Number(__ENV.GOODS_ID), optionNo: Number(__ENV.OPTION_ID) };
  }
  const pair = PAIRS[(__VU + __ITER) % PAIRS.length];
  return { goodsNo: pair.goodsNo, optionNo: pair.optionNo };
}

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

  // 매 반복 주문 생성 → 확정. LOAD_MODEL=single(기본)이면 goodsNo/optionNo는 시드의 재고
  // 넉넉한 상품으로 고정(env로 주입, README 참고 — 재고는 결제 확정 시 차감되므로 반복
  // 횟수만큼 미리 채워둬야 한다). LOAD_MODEL=spread면 pickItem()이 매 반복 다른 쌍을 고른다.
  const item = pickItem();
  const order = http.post(`${BASE}/api/v1/orders`, JSON.stringify({
    items: [{ goodsNo: item.goodsNo, optionNo: item.optionNo, quantity: 1 }],
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

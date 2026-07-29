import { api } from './client';
import type { ApiEnvelope, PageResponse } from '../types/goods';

/** POST /orders 요청 바디. 금액 필드가 없는 게 의도다 — 서버가 가격·재고를 재검증한다. */
export interface OrderCreateRequest {
  items: { goodsNo: number; optionNo: number | null; quantity: number }[];
  receiverName: string;
  receiverPhone: string;
  zipcode: string;
  address1: string;
  address2: string;
  deliveryType: 'NORMAL'; // 오늘드림은 1차 범위 밖 — 값은 하나뿐이다
}

/** payableAmount는 서버가 계산한 값 — 토스 결제창에 이 값 그대로 넘긴다(프론트가 다시 계산하지 않는다). */
export interface OrderCreateResult {
  orderNo: string;
  payableAmount: number;
}

/** POST /orders — "결제대기" 주문을 만든다. */
export async function createOrder(req: OrderCreateRequest): Promise<OrderCreateResult> {
  const response = await api.post<ApiEnvelope<OrderCreateResult>>('/orders', req);
  return response.data.data;
}

export interface PaymentConfirmResult {
  orderNo: string;
  status: string;
  paidAmount: number;
}

/**
 * POST /payments/confirm — 토스 successUrl(Task 4-11)이 호출하는 결제 승인.
 * amount는 토스에 보낸 값 검증용일 뿐, 최종 판정은 서버가 주문의 payableAmount로 한다.
 */
export async function confirmPayment(
  orderNo: string,
  paymentKey: string,
  amount: number,
): Promise<PaymentConfirmResult> {
  const response = await api.post<ApiEnvelope<PaymentConfirmResult>>('/payments/confirm', {
    orderNo,
    paymentKey,
    amount,
  });
  return response.data.data;
}

/** 주문 목록 1행 — Task 4-13(마이페이지)이 소비한다. */
export interface OrderSummary {
  orderNo: string;
  status: string;
  representativeGoodsName: string;
  itemCount: number;
  payableAmount: number;
  orderedAt: string;
}

/** GET /orders — 페이지 단위. page는 0-based(서버 계약), size 상한은 서버가 100으로 깎는다. */
export async function fetchOrders(page = 0, size = 10): Promise<PageResponse<OrderSummary>> {
  const response = await api.get<ApiEnvelope<PageResponse<OrderSummary>>>('/orders', {
    params: { page, size },
  });
  return response.data.data;
}

export interface OrderDetailItem {
  /** 취소 요청이 항목을 지목하는 유일한 키 — 상품번호가 아니라 주문항목 PK다(같은 상품이 두 줄일 수 있다). */
  orderItemId: number;
  goodsName: string;
  optionName: string;
  unitPrice: number;
  quantity: number;
  lineAmount: number;
  /** 이 항목에서 이미 취소된 수량. 잔여 = quantity − canceledQuantity. */
  canceledQuantity: number;
}

/** 취소 1회차 이력. 한 주문이 여러 번 부분취소될 수 있어 배열이다. */
export interface OrderCancelHistory {
  refundAmount: number;
  reason: string;
  canceledAt: string;
}

/** 주문 상세 — 배송지·금액·상품 전부 주문 시점 스냅샷이다(현재 상품/회원 정보를 조인하지 않는다). */
export interface OrderDetail {
  orderNo: string;
  status: string;
  totalAmount: number;
  discountAmount: number;
  payableAmount: number;
  receiverName: string;
  receiverPhone: string;
  zipcode: string;
  address1: string;
  address2: string;
  deliveryType: string;
  orderedAt: string;
  paidAt: string | null;
  items: OrderDetailItem[];
  /** 회차 합계 환불액. 서버가 더한 값이다 — 프론트가 cancels를 합산하지 않는다. */
  refundedAmount: number;
  cancels: OrderCancelHistory[];
}

/** GET /orders/{orderNo} */
export async function fetchOrderDetail(orderNo: string): Promise<OrderDetail> {
  const response = await api.get<ApiEnvelope<OrderDetail>>(`/orders/${orderNo}`);
  return response.data.data;
}

/**
 * POST /orders/{orderNo}/cancel 요청 바디. 금액 필드가 없는 게 의도다 —
 * 환불액은 서버가 주문 시점 스냅샷 단가로 계산한다(주문 생성과 같은 규칙).
 */
export interface OrderCancelRequest {
  items: { orderItemId: number; quantity: number }[];
  reason: string;
}

/** 취소 결과. status는 파생 상태다 — 잔여가 남으면 PARTIALLY_CANCELED, 전부 0이면 CANCELED. */
export interface OrderCancelResult {
  orderNo: string;
  status: string;
  refundAmount: number;
  canceledAt: string;
}

/** POST /orders/{orderNo}/cancel — 수량 단위 부분 취소. */
export async function cancelOrder(
  orderNo: string,
  req: OrderCancelRequest,
): Promise<OrderCancelResult> {
  const response = await api.post<ApiEnvelope<OrderCancelResult>>(
    `/orders/${orderNo}/cancel`,
    req,
  );
  return response.data.data;
}

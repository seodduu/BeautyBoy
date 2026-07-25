import { api } from './client';
import type { ApiEnvelope } from '../types/goods';

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

/** GET /orders */
export async function fetchOrders(): Promise<OrderSummary[]> {
  const response = await api.get<ApiEnvelope<OrderSummary[]>>('/orders');
  return response.data.data;
}

export interface OrderDetailItem {
  goodsName: string;
  optionName: string;
  unitPrice: number;
  quantity: number;
  lineAmount: number;
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
}

/** GET /orders/{orderNo} */
export async function fetchOrderDetail(orderNo: string): Promise<OrderDetail> {
  const response = await api.get<ApiEnvelope<OrderDetail>>(`/orders/${orderNo}`);
  return response.data.data;
}

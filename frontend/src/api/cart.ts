import { api } from './client';
import type { ApiEnvelope } from '../types/goods';

/** POST /cart/items 요청 바디. */
export interface CartAddRequest {
  goodsNo: number;
  optionNo: number | null;
  quantity: number;
}

/** POST /cart/items — 201과 함께 ApiResponse<Void>를 반환하므로 호출부에는 아무것도 넘기지 않는다. */
export async function addCartItem(
  goodsNo: number,
  optionNo: number | null,
  quantity: number,
): Promise<void> {
  const body: CartAddRequest = { goodsNo, optionNo, quantity };
  await api.post('/cart/items', body);
}

/**
 * 장바구니 라인. unitPrice·lineAmount는 서버가 계산해 내려준다 —
 * 프론트는 lineAmount를 그대로 합산할 뿐 단가×수량을 다시 곱하지 않는다.
 */
export interface CartItem {
  cartItemId: number;
  goodsNo: number;
  optionNo: number | null;
  goodsName: string;
  optionName: string;
  unitPrice: number;
  quantity: number;
  lineAmount: number;
  thumbnailUrl: string | null;
  stock: number; // 옵션 없는 상품은 2147483647 — min(stock, 99) 캡이 흡수한다
}

/** GET /cart/items — 장바구니 라인 목록. */
export async function fetchCartItems(): Promise<CartItem[]> {
  const response = await api.get<ApiEnvelope<CartItem[]>>('/cart/items');
  return response.data.data;
}

/** PATCH /cart/items/{id} — 수량 변경. */
export async function updateCartQuantity(cartItemId: number, quantity: number): Promise<void> {
  await api.patch(`/cart/items/${cartItemId}`, { quantity });
}

/** DELETE /cart/items/{id} — 라인 삭제. */
export async function removeCartItem(cartItemId: number): Promise<void> {
  await api.delete(`/cart/items/${cartItemId}`);
}

export interface CartBulkAddItem {
  goodsNo: number;
  optionNo: number | null;
  quantity: number;
}

/** POST /cart/items/bulk — 여러 상품을 한 번에 담기(예: 루틴 일괄 담기). */
export async function addCartItemsBulk(items: CartBulkAddItem[]): Promise<void> {
  await api.post('/cart/items/bulk', { items });
}

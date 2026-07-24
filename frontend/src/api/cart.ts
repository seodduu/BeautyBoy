import { api } from './client';

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

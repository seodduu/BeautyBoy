import { api } from './client';
import type { ApiEnvelope } from '../types/goods';
import type { GoodsIngredientResponse } from '../types/detail';

/**
 * GET /goods/:goodsNo/ingredients — 상세 페이지가 maxIrritation/maxComedogenic도 함께 쓰므로
 * ingredients 배열만 벗기지 않고 래퍼 전체를 그대로 반환한다.
 */
export async function fetchIngredients(goodsNo: number): Promise<GoodsIngredientResponse> {
  const response = await api.get<ApiEnvelope<GoodsIngredientResponse>>(
    `/goods/${goodsNo}/ingredients`,
  );
  return response.data.data;
}

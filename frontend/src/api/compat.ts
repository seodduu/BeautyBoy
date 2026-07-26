import { api } from './client';
import type { ApiEnvelope } from '../types/goods';

/** 궁합 판정 — CONFLICT(자극 중첩 등 충돌) / CAUTION(주의) / SYNERGY(함께 쓰면 좋음). */
export type CompatVerdict = 'CONFLICT' | 'CAUTION' | 'SYNERGY';

export interface CompatFinding {
  verdict: CompatVerdict;
  categoryA: string;
  categoryB: string;
  reason: string;
  goodsNos: number[];
}

/** overall === 'OK'면 findings가 비어 있고, 이때 배너는 그리지 않는다. */
export interface CompatCheckResult {
  overall: CompatVerdict | 'OK';
  findings: CompatFinding[];
}

/**
 * POST /compat/check — 설계 8장 "적용 지점 ③"(장바구니). 궁합은 조언이지 금지가 아니므로
 * 이 결과가 CONFLICT여도 주문 자체를 막지 않는다(호출부 책임).
 */
export async function checkCompat(goodsNos: number[]): Promise<CompatCheckResult> {
  const response = await api.post<ApiEnvelope<CompatCheckResult>>('/compat/check', { goodsNos });
  return response.data.data;
}

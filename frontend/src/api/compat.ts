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

/**
 * GET /compat/verdicts — 기준 상품 1개 대 후보 여러 개의 **최악 verdict 배치 판정**
 * (루틴 조합기 설계 §3.3). 메인의 체인이 단계 경계마다 1회 부른다.
 *
 * 서버는 `Map<Long, String>`을 내려주므로 JSON에서는 **키가 문자열**이다 — 조합기
 * (`composeStep`의 `verdicts`)는 goodsNo로 조회하므로 여기서 숫자 키 Map으로 바꿔 넘긴다.
 * 판정이 없는(=충돌 없는) 후보는 응답에 아예 없을 수 있어, 호출부는 `get`의 undefined를
 * "통과"로 읽는다.
 *
 * 실패는 그대로 던진다 — 게이트 생략 여부(설계 §3.3 "실패하면 게이트 없이 진행")는
 * 이 함수가 아니라 체인(useComposer)이 판단한다.
 */
export async function fetchVerdicts(
  base: number,
  candidates: number[],
): Promise<Map<number, string>> {
  const response = await api.get<ApiEnvelope<Record<string, string>>>('/compat/verdicts', {
    params: { base, candidates: candidates.join(',') },
  });
  return new Map(Object.entries(response.data.data).map(([goodsNo, v]) => [Number(goodsNo), v]));
}

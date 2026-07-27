import { fetchFlowRules } from '../../api/routine';
import type { FlowRulesResponse } from '../../types/routine';

/**
 * 규칙 fetch + ETag/localStorage 캐시(설계 §5.2·§6.5).
 *
 * 이 모듈의 계약은 하나다: **절대 던지지 않는다.** 규칙을 못 받았다고 메인이 깨지면 안 된다 —
 * 빈 규칙은 티어0(개인화 없음)으로 이어지고, 그것은 곧 현재 화면이라 사용자에게는 아무 일도
 * 일어나지 않은 것으로 보인다.
 */

/** localStorage 키. 스키마가 바뀌면 v2로 올려 옛 데이터를 자연히 버린다(events.ts와 같은 규칙). */
const STORAGE_KEY = 'bb.flowRules.v1';

/** 규칙을 하나도 못 받았을 때의 값. 매칭이 전부 빈 배열을 내므로 티어0과 결과가 같다. */
export const EMPTY_RULES: FlowRulesResponse = { version: '', flowRules: [], concernRules: [] };

function isFlowRulesResponse(value: unknown): value is FlowRulesResponse {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const rules = value as Record<string, unknown>;
  return (
    typeof rules.version === 'string' &&
    Array.isArray(rules.flowRules) &&
    Array.isArray(rules.concernRules)
  );
}

/** 저장본을 읽는다. 손상·형태 불일치면 없는 것으로 친다(events.readEvents와 같은 판단). */
function readCache(): FlowRulesResponse | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (raw === null) {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    return isFlowRulesResponse(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function writeCache(rules: FlowRulesResponse): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(rules));
  } catch {
    // 용량 초과·사파리 프라이빗 모드 등. 캐시는 최적화일 뿐이라 실패해도 이번 응답은 그대로 쓴다.
  }
}

/**
 * 규칙을 가져온다. 저장본이 있으면 그 version을 If-None-Match로 실어 보내고,
 * **304면 저장본을 그대로 쓴다**(재방문 비용 0).
 *
 * 실패 시 순서: 저장본 → 빈 규칙. 저장본이 조금 낡았을 위험보다 개인화가 통째로 사라질 위험이
 * 크다 — 규칙은 시드 전용이라 자주 바뀌지 않는다.
 */
export async function loadFlowRules(): Promise<FlowRulesResponse> {
  const cached = readCache();
  try {
    const fresh = await fetchFlowRules(cached?.version);
    if (fresh === null) {
      // 304 — 저장본이 최신이다. 저장본이 사라진 뒤 304가 오는 일은 없다(version을 안 보냈으므로).
      return cached ?? EMPTY_RULES;
    }
    writeCache(fresh);
    return fresh;
  } catch {
    return cached ?? EMPTY_RULES;
  }
}

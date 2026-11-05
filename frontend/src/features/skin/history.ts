import type { SkinRecord } from './types';

export const SKIN_HISTORY_LIMIT = 30;

export function historyKey(memberId: number): string {
  return `bb.skin.v1.${memberId}`;
}

function isRecord(x: unknown): x is SkinRecord {
  if (typeof x !== 'object' || x === null) return false;
  const r = x as Record<string, unknown>;
  return (
    r.v === 1 &&
    typeof r.at === 'string' &&
    typeof r.redness === 'number' &&
    typeof r.foreheadL === 'number' &&
    typeof r.foreheadA === 'number' &&
    typeof r.cheekA === 'number' &&
    typeof r.faceWidthRatio === 'number'
  );
}

/** 최근순. 손상(파싱 실패, 배열 아님, 원소 하나라도 형태 불일치)이면 전체를 버리고 []. */
export function readHistory(memberId: number): SkinRecord[] {
  try {
    const raw = localStorage.getItem(historyKey(memberId));
    if (raw === null) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed) || !parsed.every(isRecord)) return [];
    return parsed;
  } catch {
    return [];
  }
}

/** 앞에 넣고 30건 초과분을 버린다. quota 등 저장 예외는 삼킨다(이번 결과 표시는 그대로 한다). */
export function appendRecord(memberId: number, record: SkinRecord): void {
  const next = [record, ...readHistory(memberId)].slice(0, SKIN_HISTORY_LIMIT);
  try {
    localStorage.setItem(historyKey(memberId), JSON.stringify(next));
  } catch {
    // 저장 실패는 무시한다.
  }
}

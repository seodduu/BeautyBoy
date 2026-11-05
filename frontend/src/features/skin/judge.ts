import { GATE, SUGGESTED_CONCERN, type Measurement, type SkinRecord, type Verdict } from './types';

export function toRecord(m: Measurement, now: Date): SkinRecord {
  return { v: 1, at: now.toISOString(), redness: m.redness, foreheadL: m.foreheadL,
           foreheadA: m.foreheadA, cheekA: m.cheekA, faceWidthRatio: m.faceWidthRatio };
}

function median(xs: number[]): number {
  const s = [...xs].sort((a, b) => a - b);
  const mid = s.length >> 1;
  return s.length % 2 ? s[mid] : (s[mid - 1] + s[mid]) / 2;
}

/** history 는 최근순(0 번이 가장 최근). 이번 측정은 아직 들어 있지 않아야 한다. */
export function judge(current: Measurement, history: readonly SkinRecord[]): Verdict {
  if (history.length === 0) return { kind: 'first' };
  const window = history.slice(0, GATE.BASELINE_WINDOW);
  const comparable = window.filter(
    (r) => Math.abs(current.foreheadL - r.foreheadL) <= GATE.MAX_FOREHEAD_L_DELTA_FOR_COMPARE,
  );
  if (comparable.length === 0) {
    return { kind: 'lighting-mismatch', deltaL: current.foreheadL - window[0].foreheadL };
  }
  const baseline = median(comparable.map((r) => r.redness));
  const delta = current.redness - baseline;
  const label = Math.abs(delta) < GATE.REDNESS_NO_CHANGE ? '변화 없음' : delta > 0 ? '증가' : '감소';
  return { kind: 'change', delta, baseline, label };
}

/**
 * 제안할 고민 슬러그. 조건 밖이면 null.
 * SENSITIVE 는 affinity/profile.ts 가 이미 soothe 를 파생하므로 중복 제안을 하지 않는다.
 */
export function suggestConcern(
  verdict: Verdict,
  profile: { skinType: string | null; concerns: readonly string[] },
): 'soothe' | null {
  if (verdict.kind !== 'change' || verdict.label !== '증가') return null;
  if (profile.skinType === 'SENSITIVE') return null;
  if (profile.concerns.includes(SUGGESTED_CONCERN)) return null;
  return SUGGESTED_CONCERN;
}

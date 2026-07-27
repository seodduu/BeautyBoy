/**
 * 기기 측 관심 이벤트 — 기록·읽기·링버퍼 유지(설계 §6.1).
 *
 * 서버로 나가지 않는다. CLAUDE.md "돈과 재고는 서버, 취향은 클라이언트" 원칙 그대로,
 * 취향 신호는 브라우저 안에서 태어나고 브라우저 안에서 소비된다.
 */

/** localStorage 키. 스키마가 바뀌면 v2로 올려 옛 데이터를 자연히 버린다. */
const STORAGE_KEY = 'bb.affinity.v1';

/**
 * 링버퍼 길이. 최근성을 시간 감쇠 함수 대신 길이로 표현한다 — 감쇠 계수는 튜닝할 근거가 없고
 * 테스트도 어렵다. 50개면 한 세션(보통 5~15 이벤트)을 여러 번 덮으면서도 반년 전 취향이 남지 않는다.
 */
export const MAX_EVENTS = 50;

/** 가중치: 구매 의도의 강도 순. 조회 3회 = 담기 1회가 되도록 잡았다. 1·5·10처럼 가파르면
 *  우연히 담은 상품 하나가 프로필 전체를 지배한다. */
export const WEIGHT = { view: 1, wish: 2, cart: 3 } as const;

export interface AffinityEvent {
  goodsNo: number;
  cat3: string; // 중분류 7자
  tags: string[]; // TagView.slug[]
  w: 1 | 2 | 3;
}

/** leaf 10자(C001001001) → 중분류 7자(C001001). 규칙의 category_code가 7자다. */
export function toCat3(categoryCode: string): string {
  return categoryCode.slice(0, 7);
}

function isAffinityEvent(value: unknown): value is AffinityEvent {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const event = value as Record<string, unknown>;
  return (
    typeof event.goodsNo === 'number' &&
    typeof event.cat3 === 'string' &&
    Array.isArray(event.tags) &&
    event.tags.every((tag) => typeof tag === 'string') &&
    (event.w === 1 || event.w === 2 || event.w === 3)
  );
}

/**
 * 저장된 이벤트를 읽는다. 파싱 실패·형태 불일치면 **통째로 폐기하고 빈 배열**을 돌려준다
 * (skinProfile.ts의 readLocalSkinType 패턴). 던지지 않는다 —
 * 개인화가 안 되는 것은 폴백이 있어 안전하지만, 깨진 값으로 계산하면 무슨 일이 벌어질지 알 수 없다.
 *
 * 원소 하나만 어긋나도 전부 버린다. 성한 것만 골라 쓰면 "반쪽 프로필"이 되는데, 그 상태는
 * 재현도 설명도 안 되는 추천을 만든다. 빈 상태에서 다시 쌓는 편이 낫다.
 */
export function readEvents(): AffinityEvent[] {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (raw === null) {
    return [];
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return [];
  }

  if (!Array.isArray(parsed) || !parsed.every(isAffinityEvent)) {
    return [];
  }
  return parsed;
}

/**
 * 이벤트를 뒤에 붙이고 MAX_EVENTS 초과분을 앞에서 잘라낸다.
 *
 * `cat3`가 비면 기록하지 않는다 — 카테고리 없는 신호는 "각질 클렌징"과 "각질 토너"를 구분하지
 * 못해 단계 축이 무너진다. 이 구분이 기능의 전부이므로 반쪽 신호를 넣느니 버린다(설계 §6.1).
 */
export function recordEvent(event: AffinityEvent): void {
  if (event.cat3 === '') {
    return;
  }
  const next = [...readEvents(), event].slice(-MAX_EVENTS);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
}

/** 저장된 이벤트를 비운다. 개인화 상태를 되돌려 보고 싶을 때 쓰는 유일한 출구다. */
export function clearEvents(): void {
  localStorage.removeItem(STORAGE_KEY);
}

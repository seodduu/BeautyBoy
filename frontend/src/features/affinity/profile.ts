import type { ConcernSlug, SkinType, TextureSlug } from '../../api/auth';
import type { AffinityEvent } from './events';

/**
 * 이벤트 → 점수 집계, 프로필 병합, 티어 판정(설계 §6.2). 전부 순수 함수다.
 */

/**
 * 티어2로 올라가는 이벤트 수. 이벤트 1~2개는 유입 경로(검색·배너)의 잔상일 뿐 취향이 아니다.
 * 5개면 최소 두 상품 이상을 능동적으로 본 상태다. 미만이면 티어1로 내려가 프로필로 개인화하므로,
 * 로그인 회원에게 "아무것도 안 바뀌는 상태"는 프로필이 비어 있을 때뿐이다.
 */
const BEHAVIOR_TIER_THRESHOLD = 5;

/**
 * 피부타입 → 파생 고민 태그. 고민을 하나도 안 고른 회원에게만 쓰는 약한 기본값(설계 §6.2).
 *
 * `gentle`은 CONCERNS 상수에 없지만 concern_target_rule에는 있다 — 프로필에서 직접 고를 수는
 * 없고 SENSITIVE에서만 파생되는 슬러그라 타입을 `ConcernSlug | 'gentle'`로 넓혀 받는다.
 */
export const SKIN_TYPE_CONCERNS: Record<SkinType, DerivedConcern[]> = {
  DRY: ['moisture', 'barrier'],
  OILY: ['sebum', 'pore'],
  COMBINATION: ['moisture', 'sebum'],
  SENSITIVE: ['soothe', 'gentle'],
};

/** 매칭에 실리는 고민 슬러그 — 직접 고르는 9종 + 파생 전용 `gentle`. */
export type DerivedConcern = ConcernSlug | 'gentle';

/** 프로필에서 직접 고를 수 있는 고민 9종(설계 §4.1). 서버 concerns의 부분집합 판정에 쓴다. */
const CONCERN_SLUGS: readonly ConcernSlug[] = [
  'exfoliate',
  'sebum',
  'pore',
  'trouble',
  'soothe',
  'moisture',
  'barrier',
  'bright',
  'anti-aging',
];

/** 선호 사용감 3종. 같은 concerns 컬럼에 고민과 섞여 저장된다(설계 §4.1). */
const TEXTURE_SLUGS: readonly TextureSlug[] = ['fresh', 'dewy', 'matte'];

/**
 * (cat3 × tag) 점수 집계. 키는 `${cat3}|${tag}`, 값은 가중치 합.
 *
 * 카테고리를 키에 넣는 것이 이 함수의 전부다 — 태그만 세면 "각질 클렌징"과 "각질 토너"가
 * 한 덩어리가 되어 단계별 추천이라는 목적 자체가 사라진다.
 */
export function aggregate(events: AffinityEvent[]): Map<string, number> {
  const scores = new Map<string, number>();
  for (const event of events) {
    for (const tag of event.tags) {
      const key = `${event.cat3}|${tag}`;
      scores.set(key, (scores.get(key) ?? 0) + event.w);
    }
  }
  return scores;
}

/**
 * 3단 사다리 판정(설계 §6.2).
 * - 2: 행동 신호가 충분하다 → flowRules 매칭
 * - 1: 프로필만 있다 → concernRules 매칭
 * - 0: 둘 다 없다 → 개인화 없음(현재 화면 그대로)
 *
 * `concerns`는 effectiveConcerns를 통과한 값을 넘긴다 — 피부타입 파생까지 끝난 뒤라야
 * "프로필이 비었다"가 정확해진다.
 */
export function tierOf(events: AffinityEvent[], concerns: string[]): 0 | 1 | 2 {
  if (events.length >= BEHAVIOR_TIER_THRESHOLD) {
    return 2;
  }
  return concerns.length > 0 ? 1 : 0;
}

/**
 * 서버 프로필(고민 + 사용감이 한 컬럼에 섞여 온다) → 매칭에 쓸 고민 목록.
 *
 * 고민이 하나라도 있으면 그대로 쓰고, **비어 있을 때만** 피부타입에서 파생한다 —
 * 직접 고른 것이 추론한 것을 이긴다. 선택 순서를 보존하는 것이 중요하다(티어1의 우선순위다).
 */
export function effectiveConcerns(concerns: string[], skinType: SkinType | null): DerivedConcern[] {
  const picked = concerns.filter((slug): slug is ConcernSlug =>
    (CONCERN_SLUGS as readonly string[]).includes(slug),
  );
  if (picked.length > 0) {
    return picked;
  }
  return skinType ? SKIN_TYPE_CONCERNS[skinType] : [];
}

/**
 * 서버 프로필에서 사용감 슬러그만 골라낸다. 후보 tie-break에만 쓰이고 매칭 점수에는 관여하지
 * 않는다 — 사용감은 "무엇을 추천할까"가 아니라 "같은 후보 중 무엇을 앞에 둘까"의 축이다.
 */
export function preferredTextures(concerns: string[]): TextureSlug[] {
  return concerns.filter((slug): slug is TextureSlug =>
    (TEXTURE_SLUGS as readonly string[]).includes(slug),
  );
}

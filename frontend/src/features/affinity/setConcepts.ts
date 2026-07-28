import type { SkinType } from '../../api/auth';
import { SKIN_TYPE_CONCERNS, effectiveConcerns, type DerivedConcern } from './profile';

/** 세트 수. 탭 3개가 한 줄에 들어가는 최대치이자 고르는 부담이 없는 수. */
export const SET_COUNT = 3;

/** 세트 표시 문자(A, B, C…) — SET_COUNT에서 자동 생성돼 개수가 어긋날 일이 없다. */
export const SET_LETTERS: readonly string[] = Array.from({ length: SET_COUNT }, (_, i) =>
  String.fromCharCode(65 + i),
);

/**
 * 최후 폴백 3종 — 비로그인·무프로필에게 보이는 대표 컨셉.
 * pore/trouble은 남성 지성 피부 최빈 고민, moisture가 건성 계열을 덮는다.
 * 세 슬러그 모두 concern_target_rule에 타겟 규칙이 있어 reason 문장이 보장된다.
 */
export const FALLBACK_CONCEPTS: DerivedConcern[] = ['pore', 'trouble', 'moisture'];

/**
 * 탭 라벨. 고민 9종은 SkinProfileFields.tsx CONCERNS와 같은 문구여야 한다
 * (setConcepts.test.ts가 동일성을 단언한다). gentle만 이 파일이 원본이다 —
 * 프로필에서 직접 못 고르는 파생 전용 슬러그라 CONCERNS에 없다.
 */
const CONCEPT_LABELS: Record<DerivedConcern, string> = {
  exfoliate: '각질',
  sebum: '피지',
  pore: '모공',
  trouble: '트러블',
  soothe: '진정',
  moisture: '보습',
  barrier: '장벽',
  bright: '브라이트닝',
  'anti-aging': '안티에이징',
  gentle: '저자극',
};

export interface SetConcept {
  /** composeStep의 signals.concerns에 단독으로 들어갈 슬러그. */
  slug: DerivedConcern;
  label: string;
  /** false면 고정 폴백 — 전부 false일 때 화면이 프로필 등록 유도 문구를 낸다. */
  personalized: boolean;
}

/**
 * 파생 사다리(스펙 §4): ① 직접 고른 고민(선택 순서, 비었으면 피부타입 파생 — effectiveConcerns)
 * → ② 피부타입 파생 보충 → ③ 고정 폴백. 중복은 제거하고 항상 정확히 SET_COUNT개.
 */
export function deriveSetConcepts(
  concerns: string[],
  skinType: SkinType | null,
): SetConcept[] {
  const out: SetConcept[] = [];
  const push = (slug: DerivedConcern, personalized: boolean) => {
    if (out.length < SET_COUNT && !out.some((c) => c.slug === slug)) {
      out.push({ slug, label: CONCEPT_LABELS[slug], personalized });
    }
  };
  for (const slug of effectiveConcerns(concerns, skinType)) push(slug, true);
  if (skinType) for (const slug of SKIN_TYPE_CONCERNS[skinType]) push(slug, true);
  for (const slug of FALLBACK_CONCEPTS) push(slug, false);
  return out;
}

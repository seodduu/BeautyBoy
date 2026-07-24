/**
 * 백엔드 GoodsAssessmentResponse / FlaggedIngredient와 1:1로 맞춘 판정 타입.
 * (backend/src/main/java/com/beautyboy/ingredient/dto/*)
 */
export type VerdictCode =
  | 'NO_CONCERN'
  | 'MOSTLY_FINE'
  | 'CHECK_SENSITIVE'
  | 'CAUTION'
  | 'REVIEW';

/** 화면 분류: 확인필요(착향제/각질산) | 참고(한도) | 검토(금지). */
export type FlagAxis = 'CHECK' | 'INFO' | 'REVIEW';

export interface FlaggedIngredient {
  ingredientId: number;
  name: string;
  inciName: string;
  /** 성분별 설명(ingredient.summary) — 성분마다 다르다. */
  summary: string;
  flags: string[];
  axis: FlagAxis;
  /** 각질산 계열("AHA"|"BHA") — 분류지 근거가 아니다. 아니면 null. */
  acidClass: string | null;
  /** 배합한도 원문(식약처 사용제한) — 한도 성분일 때만. 아니면 null. */
  limitText: string | null;
}

export interface GoodsAssessment {
  goodsNo: number;
  verdictCode: VerdictCode;
  verdictText: string;
  checkCount: number;
  rinseOff: boolean;
  flagged: FlaggedIngredient[];
}

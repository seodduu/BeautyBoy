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
  flags: string[];
  axis: FlagAxis;
  sourceRef: string | null;
}

export interface GoodsAssessment {
  goodsNo: number;
  verdictCode: VerdictCode;
  verdictText: string;
  checkCount: number;
  rinseOff: boolean;
  flagged: FlaggedIngredient[];
}

import { api } from './client';
import type { ApiEnvelope, GoodsListItem } from '../types/goods';

/** 설계 8장 "루틴 가이드" — 프로필/퀴즈로 정해지는 피부타입 4종. */
export type SkinType = 'DRY' | 'OILY' | 'COMBINATION' | 'SENSITIVE';

export interface RoutineStepResponse {
  stepOrder: number;
  stepName: string;
  beginnerTip: string;
  recommendations: GoodsListItem[];
}

export interface RoutineResponse {
  templateId: number;
  name: string;
  skinType: SkinType;
  time: string;
  description: string;
  steps: RoutineStepResponse[];
}

/**
 * GET /routines — 피부타입×시간대 단순 룩업(설계 8장 "1차: 템플릿 매칭").
 * 인증 불필요(SecurityConfig가 GET을 permitAll) — 비회원도 퀴즈 결과만으로 조회할 수 있다.
 */
export async function fetchRoutine(skinType?: SkinType, time?: string): Promise<RoutineResponse> {
  const response = await api.get<ApiEnvelope<RoutineResponse>>('/routines', {
    params: { skinType, time },
  });
  return response.data.data;
}

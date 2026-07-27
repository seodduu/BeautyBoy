import { api } from './client';
import type { ApiEnvelope, GoodsListItem } from '../types/goods';
import type { FlowRulesResponse } from '../types/routine';

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

/**
 * GET /routine/flow-rules — 규칙 전량 배포(설계 §5.2). 인증 불필요(permitAll).
 *
 * `version`을 주면 If-None-Match로 실어 보내고, 서버가 304로 끊으면 **null**을 돌려준다
 * ("저장본을 그대로 쓰라"는 뜻). axios 기본 validateStatus는 304를 에러로 던지므로 성공 범위에
 * 명시적으로 넣는다 — 여기서 304는 실패가 아니라 이 API의 정상 경로다.
 *
 * 캐시 저장·폴백은 이 함수가 아니라 features/affinity/flowRules.ts가 맡는다. 여기는 HTTP 계약만 안다.
 */
export async function fetchFlowRules(version?: string | null): Promise<FlowRulesResponse | null> {
  const response = await api.get<ApiEnvelope<FlowRulesResponse>>('/routine/flow-rules', {
    headers: version ? { 'If-None-Match': version } : undefined,
    validateStatus: (status) => status === 304 || (status >= 200 && status < 300),
  });
  return response.status === 304 ? null : response.data.data;
}

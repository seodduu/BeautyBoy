import { api, refreshSession } from './client';
import type { RefreshSessionResult } from './client';
import type { MemberInfo } from '../stores/authStore';

/**
 * refresh 실제 구현은 client.ts에 있다(부트스트랩·401 인터셉터 두 소비자가 in-flight
 * promise 한 벌을 공유해야 하므로). 이 파일은 호출부(App 등) 호환을 위해 재수출만 한다 —
 * in-flight 공유 근거는 client.ts:33-46 참고.
 */
export { refreshSession, type RefreshSessionResult };

/** 피부타입 — 백엔드 계약(Task 4·5)과 동일한 4종. */
export type SkinType = 'DRY' | 'OILY' | 'COMBINATION' | 'SENSITIVE';

/** 피부 고민 — 백엔드 계약과 동일한 값. */
export type Concern = 'PORE' | 'TROUBLE' | 'WRINKLE' | 'DARK_SPOT';

/** 연령대 — 백엔드 계약과 동일한 값. */
export type AgeBand = '10s' | '20s' | '30s' | '40s' | '50s+';

export interface SignupPayload {
  email: string;
  password: string;
  nickname: string;
  skinType?: SkinType;
  concerns?: Concern[];
  ageBand?: AgeBand;
}

export interface SignupResult {
  id: number;
  email: string;
  nickname: string;
  grade: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface LoginResult {
  accessToken: string;
}

interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
}

/** POST /auth/signup — 성공 시 생성된 회원 요약을 반환한다. */
export async function signup(payload: SignupPayload): Promise<SignupResult> {
  const response = await api.post<ApiEnvelope<SignupResult>>('/auth/signup', payload);
  return response.data.data;
}

/** POST /auth/login — 성공 시 accessToken을 반환한다(리프레시 토큰은 httpOnly 쿠키로 별도 전달). */
export async function login(payload: LoginPayload): Promise<LoginResult> {
  const response = await api.post<ApiEnvelope<LoginResult>>('/auth/login', payload);
  return response.data.data;
}

/**
 * GET /members/me — 로그인 직후 Header 닉네임 표시를 위해 회원 정보를 채운다.
 * 로그인 응답에는 accessToken만 있고 닉네임이 없으므로 별도로 호출한다.
 */
export async function fetchMe(): Promise<MemberInfo> {
  const response = await api.get<ApiEnvelope<MemberInfo>>('/members/me');
  return response.data.data;
}

/** POST /auth/logout — 서버의 리프레시 세션(쿠키)을 무효화한다. */
export async function logout(): Promise<void> {
  await api.post('/auth/logout');
}

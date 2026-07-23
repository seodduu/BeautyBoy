import { api } from './client';
import type { MemberInfo } from '../stores/authStore';

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

export interface RefreshSessionResult {
  accessToken: string;
  member: MemberInfo;
}

/**
 * POST /auth/refresh — 앱 부트스트랩 시 세션 복구를 1회 시도한다.
 * 실패(401 등)는 호출부(App)가 조용히 로그아웃 상태로 진행하며,
 * client.ts의 401 인터셉터는 이 요청 자체를 refresh 요청으로 인식해 추가 refresh를 부르지 않는다.
 */
export async function refreshSession(): Promise<RefreshSessionResult> {
  const response = await api.post<ApiEnvelope<RefreshSessionResult>>('/auth/refresh');
  return response.data.data;
}

/** POST /auth/logout — 서버의 리프레시 세션(쿠키)을 무효화한다. */
export async function logout(): Promise<void> {
  await api.post('/auth/logout');
}

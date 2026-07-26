import { api, refreshSession } from './client';
import type { RefreshSessionResult } from './client';
import type { MemberInfo } from '../stores/authStore';

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

/*
 * POST /auth/refresh — 앱 부트스트랩 시 세션 복구를 1회 시도한다.
 * 실패(401 등)는 호출부(App)가 조용히 로그아웃 상태로 진행하며,
 * client.ts의 401 인터셉터는 이 요청 자체를 refresh 요청으로 인식해 추가 refresh를 부르지 않는다.
 *
 * **왜 in-flight 공유인가 (Task 4-16a):**
 * 리프레시 토큰은 한 번 쓰면 회전한다. 그래서 같은 토큰으로 두 요청이 동시에 나가면 서버가
 * 한쪽을 409(`AUTH_REFRESH_CONFLICT`)로 돌려보낸다 — 이제 500이 아니라 정상 계약이다.
 * 문제는 **패배자가 먼저 resolve할 수 있다**는 것이다. React StrictMode는 개발 모드에서
 * 부트스트랩 이펙트를 두 번 실행하는데, 살아남는 쪽(두 번째)이 하필 패배자면 세션을 지우지
 * 않아도 `accessToken`이 null인 채 `isBootstrapping=false`가 되어 `RequireAuth`가 `/login`으로
 * 튕긴다. 애초에 서버에 요청을 하나만 보내면 승패 자체가 생기지 않는다.
 *
 * **구현 위치 (Task 1):** in-flight 캐시는 예전에 이 파일(`inFlightRefresh`)과 client.ts의
 * 401 인터셉터(`refreshPromise`)가 각각 한 벌씩 따로 가지고 있었다. 두 캐시가 따로 놀면
 * 부트스트랩과 인터셉터가 동시에 refresh를 쏠 때 중복 요청이 나가고, 그 중복이 백엔드
 * 409를 만드는 조건이 된다. 그래서 실제 구현은 `client.ts`의 `refreshSession()`으로 합치고
 * 이 파일은 재수출만 한다 — client.ts가 `api` 인스턴스를 소유해 순환 import 없이 두 소비자를
 * 한 곳에서 공유할 수 있는 쪽이기 때문이다.
 */

/** POST /auth/logout — 서버의 리프레시 세션(쿠키)을 무효화한다. */
export async function logout(): Promise<void> {
  await api.post('/auth/logout');
}

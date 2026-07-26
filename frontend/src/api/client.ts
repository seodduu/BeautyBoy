import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore, type MemberInfo } from '../stores/authStore';

/**
 * 공용 axios 인스턴스.
 * - baseURL '/api/v1' — 백엔드는 dev에서 vite.config.ts 프록시(/api → 8080)를 경유한다.
 * - withCredentials: true — httpOnly 리프레시 쿠키를 요청/응답에 실어야 하므로 필수.
 */
export const api = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
});

// 재시도 1회 여부를 표시하는 내부 플래그 — 무한루프 방지용.
interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
}

// 요청 인터셉터: 메모리에 있는 accessToken을 Authorization 헤더로 첨부.
api.interceptors.request.use((config) => {
  const { accessToken } = useAuthStore.getState();
  if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`);
  }
  return config;
});

// 리프레시 요청 자체는 재시도 대상에서 제외하기 위한 경로 판별.
function isRefreshRequest(url: string | undefined): boolean {
  return !!url && url.includes('/auth/refresh');
}

/**
 * `/auth/refresh` 응답 데이터 — 부트스트랩(api/auth.ts)과 인터셉터(이 파일)가 공유한다.
 * 리프레시 토큰은 한 번 쓰면 회전하므로, 두 소비자가 각자 요청을 쏘면 서버가 한쪽을
 * 409(`AUTH_REFRESH_CONFLICT`)로 돌려보낸다. 그래서 in-flight promise를 한 벌로만 두고
 * 두 소비자가 공유해야 한다(Task 1).
 */
export interface RefreshSessionResult {
  accessToken: string;
  member: MemberInfo;
}

// 동시에 여러 요청/호출자가 리프레시를 원해도 서버로는 한 번만 나가도록 공유하는 in-flight promise.
// client.ts(401 인터셉터)와 api/auth.ts(부트스트랩)가 이 하나만 사용한다 — 두 벌로 나누지 않는다.
let refreshPromise: Promise<RefreshSessionResult> | null = null;

/** POST /auth/refresh — 인터셉터 재시도와 부트스트랩 세션 복구가 공유하는 유일한 in-flight 창구. */
export async function refreshSession(): Promise<RefreshSessionResult> {
  if (!refreshPromise) {
    refreshPromise = api
      .post<{ code: string; data: RefreshSessionResult }>('/auth/refresh')
      .then((response) => {
        const result = response.data.data;
        useAuthStore.getState().setAuth(result.accessToken);
        return result;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

// 응답 인터셉터: 401이면 /auth/refresh를 1회 시도한 뒤 원요청을 새 토큰으로 재실행한다.
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetriableRequestConfig | undefined;
    const status = error.response?.status;

    if (status !== 401 || !originalRequest) {
      return Promise.reject(error);
    }

    // refresh 자체가 401이면 다시 refresh를 부르지 않는다 — 그대로 실패 처리하고 세션을 비운다.
    if (isRefreshRequest(originalRequest.url)) {
      useAuthStore.getState().clear();
      return Promise.reject(error);
    }

    // 이미 한 번 재시도했다면 더 이상 갱신을 시도하지 않는다 (무한루프 방지).
    if (originalRequest._retried) {
      useAuthStore.getState().clear();
      return Promise.reject(error);
    }

    originalRequest._retried = true;

    try {
      const { accessToken: newToken } = await refreshSession();
      originalRequest.headers.set('Authorization', `Bearer ${newToken}`);
      return api(originalRequest);
    } catch (refreshError) {
      // 리프레시 실패 원인을 구분한다: 401(진짜 세션 없음)일 때만 세션을 지운다.
      // 409(AUTH_REFRESH_CONFLICT)·5xx·네트워크 오류는 "지금 확인 못 했을 뿐"이므로
      // 세션을 유지한다 — App.tsx의 isSessionGone()과 동일한 판단 기준(Task 1).
      if (axios.isAxiosError(refreshError) && refreshError.response?.status === 401) {
        useAuthStore.getState().clear();
      }
      return Promise.reject(refreshError);
    }
  },
);

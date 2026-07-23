import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '../stores/authStore';

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

// 동시에 여러 요청이 401을 받아도 refresh는 한 번만 나가도록 in-flight promise를 공유한다.
let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = api
      .post<{ code: string; data: { accessToken: string } }>('/auth/refresh')
      .then((response) => {
        const { accessToken } = response.data.data;
        useAuthStore.getState().setAuth(accessToken);
        return accessToken;
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
      const newToken = await refreshAccessToken();
      originalRequest.headers.set('Authorization', `Bearer ${newToken}`);
      return api(originalRequest);
    } catch (refreshError) {
      useAuthStore.getState().clear();
      return Promise.reject(refreshError);
    }
  },
);

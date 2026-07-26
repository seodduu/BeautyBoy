import { useEffect } from 'react';
import axios from 'axios';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { router } from './router';
import { useAuthStore } from './stores/authStore';
import { refreshSession } from './api/auth';

const queryClient = new QueryClient();

/**
 * "이 실패는 세션이 없다는 뜻인가?" — 오직 401만 그렇다.
 *
 * 409(`AUTH_REFRESH_CONFLICT`)·5xx·네트워크 오류는 "지금 확인하지 못했다"일 뿐이며,
 * 그때 세션을 지우면 다른 호출이 방금 정상 복구한 로그인 상태까지 날아간다(Task 4-16a).
 * 서버가 상태 코드로 "인증 실패"와 "경쟁에서 졌다"를 구분해주므로 여기서는 그것만 읽으면 된다.
 */
function isSessionGone(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 401;
}

function App() {
  useEffect(() => {
    // 앱 부트스트랩: 리프레시 쿠키로 세션 복구를 1회 시도한다.
    // 실패(401 등)는 조용히 로그아웃 상태로 진행 — 페이지 전체를 막지 않는다.
    // 이 요청 자체가 /auth/refresh이므로, client.ts의 401 인터셉터는 이를
    // "refresh 요청"으로 인식해 추가 refresh를 부르지 않는다(재귀 방지, 기존 in-flight 공유 유지).
    let cancelled = false;

    async function bootstrap() {
      try {
        const { accessToken, member } = await refreshSession();
        if (!cancelled) {
          useAuthStore.getState().setAuth(accessToken, member);
        }
      } catch (error) {
        // 401(세션 없음)일 때만 비운다. 그 외 실패로 유효 세션을 파괴하지 않는다.
        if (!cancelled && isSessionGone(error)) {
          useAuthStore.getState().clear();
        }
      } finally {
        if (!cancelled) {
          useAuthStore.getState().setBootstrapping(false);
        }
      }
    }

    bootstrap();

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}

export default App;

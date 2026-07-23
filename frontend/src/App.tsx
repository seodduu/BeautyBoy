import { useEffect } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { router } from './router';
import { useAuthStore } from './stores/authStore';
import { refreshSession } from './api/auth';

const queryClient = new QueryClient();

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
      } catch {
        if (!cancelled) {
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

import { useEffect } from 'react';
import axios from 'axios';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { router } from './router';
import { useAuthStore } from './stores/authStore';
import { refreshSession } from './api/auth';
import { installSessionCacheReset } from './features/auth/sessionCacheReset';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      /**
       * 60초. 이 앱에서 가장 빨리 변하는 조회 데이터가 랭킹(집계 주기 분 단위)과
       * 재고 표시이고, 그보다 짧게 잡을 이유가 없다. 60초면 한 화면 안에서 오가는 동안
       * 재요청이 없고, 탭을 오래 비웠다 오면 다시 받는다.
       */
      staleTime: 60_000,
      /**
       * 5분. 화면을 떠나도 캐시를 이 시간만큼 들고 있어 뒤로가기가 즉시 그려진다.
       * staleTime보다 길어야 의미가 있다(짧으면 신선한 데이터를 버리게 된다).
       */
      gcTime: 5 * 60_000,
      /**
       * 끈다. 탭 전환은 "데이터가 낡았다"는 신호가 아니다 — 여기가 켜져 있어서
       * 알트탭 한 번에 화면 전체가 재요청을 걸었다. 신선도는 staleTime이 책임진다.
       */
      refetchOnWindowFocus: false,
      /**
       * 1회. 기본값 3회는 백엔드가 죽었을 때 오류 화면이 뜨기까지 손님을 오래 기다리게 한다.
       * 일시적 네트워크 실패는 1회 재시도로 대부분 걷히고, 진짜 장애는 빨리 드러나야 한다.
       */
      retry: 1,
    },
    mutations: {
      /** 재시도하지 않는다. 담기·주문·결제는 중복 실행이 곧 사고다. */
      retry: 0,
    },
  },
});

// 세션 경계(로그아웃·계정 전환·부트스트랩 복구)에서 회원 스코프 캐시를 버린다(설계 §6.6).
// useEffect가 아니라 모듈 최상단에서 한 번만 부르는 이유: 세션 변화는 React 트리의
// 생애가 아니라 앱의 생애에 걸린 사건이고, StrictMode의 이중 마운트로 구독이 두 번
// 걸리는 문제를 애초에 만들지 않는다.
installSessionCacheReset(queryClient);

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

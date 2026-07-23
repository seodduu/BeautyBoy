import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';

interface RequireAuthProps {
  children: ReactNode;
}

/**
 * 로그인이 필요한 라우트를 감싸는 가드.
 *
 * isBootstrapping 동안에는 절대 리다이렉트하지 않는다 — App이 /auth/refresh로 세션을
 * 복원하기 전에 판정하면, 로그인한 사용자도 새로고침할 때마다 /login으로 튕긴다.
 * 그동안은 빈 화면 대신 대기 안내를 둔다(DESIGN.md UX 계약: 빈 화면을 흰 여백으로 방치하지 않는다).
 */
export function RequireAuth({ children }: RequireAuthProps) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const isBootstrapping = useAuthStore((state) => state.isBootstrapping);

  if (isBootstrapping) {
    return (
      <p role="status" className="bb-auth-pending">
        로그인 상태를 확인하는 중입니다…
      </p>
    );
  }

  if (!accessToken) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}

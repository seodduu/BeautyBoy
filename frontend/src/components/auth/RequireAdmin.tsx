import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';

interface RequireAdminProps {
  children: ReactNode;
}

/**
 * `/admin/*` 라우트를 감싸는 가드.
 *
 * **이것은 편의 가드일 뿐 보안이 아니다.** 이 컴포넌트를 우회해도(devtools로 상태를 조작하는 등)
 * 실제 admin API 호출은 서버의 `@PreAuthorize("hasRole('ADMIN')")`가 막는다 — 그게 진짜 판정이다.
 * 여기서는 그저 권한 없는 사용자에게 빈 화면/403 대신 자연스러운 리다이렉트를 보여줄 뿐이다.
 *
 * role은 오직 `GET /members/me` 응답(authStore.member.role)에서만 온다 — JWT를 프론트에서
 * 디코드해 꺼내지 않는다(project law: 토큰 파싱을 클라이언트가 시작하면 신뢰 경계가 흐려진다).
 *
 * RequireAuth와 같은 이유로 isBootstrapping 동안에는 절대 리다이렉트하지 않는다 — 새로고침
 * 직후 /auth/refresh가 세션을 복원하기 전에 판정하면 ADMIN도 매번 튕긴다.
 */
export function RequireAdmin({ children }: RequireAdminProps) {
  const member = useAuthStore((state) => state.member);
  const isBootstrapping = useAuthStore((state) => state.isBootstrapping);

  if (isBootstrapping) {
    return (
      <p role="status" className="bb-auth-pending">
        로그인 상태를 확인하는 중입니다…
      </p>
    );
  }

  if (member?.role !== 'ADMIN') {
    return <Navigate to="/main" replace />;
  }

  return <>{children}</>;
}

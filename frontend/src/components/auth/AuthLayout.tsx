import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { WaveCanvas } from '../landing/WaveCanvas';
import './AuthLayout.css';

interface AuthLayoutProps {
  children: ReactNode;
}

/**
 * 로그인·가입 공통 셸.
 *
 * 온보딩(검정 히어로)에서 밝은 앱으로 넘어가는 다리 화면이라, 왼쪽은 온보딩의
 * 웨이브 + 워드마크를 그대로 계승한 검정 패널, 오른쪽은 폼이다.
 * 밝기 계단: 온보딩(검정) → 이 화면(검정 절반) → 메인(검정 밴드 → 흰 콘텐츠).
 *
 * 이 화면에서는 공용 헤더를 렌더하지 않는다(Header가 /login·/signup을 감지해 null 반환) —
 * 왼쪽 패널의 워드마크가 브랜드 마크이자 홈 링크를 겸한다.
 */
export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <div className="bb-auth-shell">
      <aside className="bb-auth-shell__brand">
        <WaveCanvas />
        <Link to="/" className="bb-auth-shell__wordmark" aria-label="뷰티보이 홈으로">
          Beauty Boy
        </Link>
      </aside>
      <div className="bb-auth-shell__panel">{children}</div>
    </div>
  );
}

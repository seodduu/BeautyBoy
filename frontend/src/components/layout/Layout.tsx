import { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Header } from './Header';
import './Layout.css';

/**
 * 모든 라우트를 감싸는 공통 셸: 헤더 + 메인(Outlet).
 * 부모 라우트 1개로 등록되므로, 자식 라우트가 늘어나도 Layout 인스턴스는 항상 1개다.
 */
export function Layout() {
  const { pathname } = useLocation();

  // pathname 전환 시 최상단으로. Main.css의 전역 scroll-behavior: smooth가
  // 개입하지 못하게 instant를 명시한다. 쿼리스트링·해시 변경은 리셋하지 않는다.
  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
  }, [pathname]);

  return (
    <div className="bb-layout">
      <a className="bb-skip-link" href="#main-content">
        본문 바로가기
      </a>
      <Header />
      <main id="main-content" className="bb-layout__main">
        <Outlet />
      </main>
    </div>
  );
}

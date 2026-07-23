import { Outlet } from 'react-router-dom';
import { Header } from './Header';
import { Footer } from './Footer';
import './Layout.css';

/**
 * 모든 라우트를 감싸는 공통 셸: 헤더 + 메인(Outlet) + 푸터.
 * 부모 라우트 1개로 등록되므로, 자식 라우트가 늘어나도 Layout 인스턴스는 항상 1개다.
 */
export function Layout() {
  return (
    <div className="bb-layout">
      <Header />
      <main className="bb-layout__main">
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}

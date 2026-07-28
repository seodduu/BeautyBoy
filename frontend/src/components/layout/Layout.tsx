import { Outlet, ScrollRestoration } from 'react-router-dom';
import { Header } from './Header';
import { Footer } from './Footer';
import './Layout.css';

/**
 * 모든 라우트를 감싸는 공통 셸: 헤더 + 메인(Outlet).
 * 부모 라우트 1개로 등록되므로, 자식 라우트가 늘어나도 Layout 인스턴스는 항상 1개다.
 *
 * 스크롤은 데이터 라우터의 `<ScrollRestoration>`이 맡는다 — 새 이동(push)은 최상단,
 * 뒤로/앞으로는 세션 히스토리 키 기준으로 이전 위치 복원이다. 직접 쓰던 pathname effect를
 * 이걸로 대체했다(바퀴를 유지보수할 이유가 없다). 목록 페이지 전환의 스크롤은 히스토리
 * 복원이 아니라 화면 규칙이라 GoodsList가 따로 처리한다(DESIGN.md `pager`).
 */
export function Layout() {
  return (
    <div className="bb-layout">
      <ScrollRestoration />
      <a className="bb-skip-link" href="#main-content">
        본문 바로가기
      </a>
      <Header />
      <main id="main-content" className="bb-layout__main">
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}

import { Suspense } from 'react';
import { Outlet, ScrollRestoration } from 'react-router-dom';
import { Header } from './Header';
import { Footer } from './Footer';
import { Skeleton } from '../ui/Skeleton';
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
        {/* 지연 로딩 라우트의 폴백. 스켈레톤 한 블록으로 통일한다 — 화면마다 다른 폴백을 두면
            "로딩 중"이 화면마다 다르게 생기고, 그 자체가 레이아웃 점프의 원인이 된다.
            (DESIGN.md "상태: 로딩·빈 상태·진행" — 300ms 넘는 로딩은 스켈레톤) */}
        <Suspense fallback={<Skeleton ratio="16 / 6" className="bb-layout__fallback" />}>
          <Outlet />
        </Suspense>
      </main>
      <Footer />
    </div>
  );
}

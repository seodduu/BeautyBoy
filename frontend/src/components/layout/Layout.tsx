import type { PropsWithChildren } from 'react';
import { Header } from './Header';
import { Footer } from './Footer';
import './Layout.css';

/**
 * 모든 라우트를 감싸는 공통 셸: 헤더 + 메인 + 푸터.
 */
export function Layout({ children }: PropsWithChildren) {
  return (
    <div className="bb-layout">
      <Header />
      <main className="bb-layout__main">{children}</main>
      <Footer />
    </div>
  );
}

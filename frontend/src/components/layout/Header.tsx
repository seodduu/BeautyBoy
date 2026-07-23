import { Link } from 'react-router-dom';
import './Header.css';

/**
 * 올리브영식 헤더: 로고 / 검색바 자리(실제 검색은 후속 웨이브) / 장바구니·로그인.
 */
export function Header() {
  return (
    <header className="bb-header">
      <div className="bb-header__bar">
        <Link to="/" className="bb-header__logo" aria-label="뷰티보이 홈으로">
          BEAUTY BOY<span className="bb-header__logo-dot">.</span>
        </Link>

        {/* 검색 기능은 이 태스크의 범위가 아님 — 자리만 확보 */}
        <div className="bb-header__search" role="search" aria-label="상품 검색(준비 중)">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <circle cx="7" cy="7" r="5" stroke="currentColor" strokeWidth="1.4" />
            <path d="M11 11L14.5 14.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
          </svg>
          <span className="bb-header__search-text">스킨케어, 클렌징, 헤어 검색</span>
        </div>

        <nav className="bb-header__nav" aria-label="주요 메뉴">
          {/* 장바구니 페이지는 후속 웨이브 범위 — 라우트 없이 자리만 표시 */}
          <span className="bb-header__nav-link" aria-label="장바구니(준비 중)">
            장바구니
            <span className="bb-header__cart-count">0</span>
          </span>
          <Link to="/login" className="bb-header__nav-link">
            로그인
          </Link>
        </nav>
      </div>
    </header>
  );
}

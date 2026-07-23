import { Link, useLocation, useNavigate } from 'react-router-dom';
import './Header.css';
import { useAuthStore } from '../../stores/authStore';
import { logout } from '../../api/auth';

const LANDING_NAV = ['About', 'Work', 'Services', 'Packages', 'Contact'] as const;

/**
 * 올리브영식 헤더: 로고 / 검색바 자리(실제 검색은 후속 웨이브) / 장바구니·로그인.
 * 로그인 상태(authStore.member)면 "로그인" 링크 대신 닉네임 + 로그아웃 버튼을 표시한다.
 * 앱 부트스트랩(세션 복구) 진행 중에는 이 영역을 스켈레톤으로 대체해 깜빡임을 막는다.
 */
export function Header() {
  const member = useAuthStore((state) => state.member);
  const isBootstrapping = useAuthStore((state) => state.isBootstrapping);
  const clear = useAuthStore((state) => state.clear);
  const navigate = useNavigate();
  /* 랜딩(/)은 검정 풀블리드 히어로라 흰 헤더 바가 얹히면 화면이 두 조각으로 잘린다.
     그 화면에서만 헤더를 투명하게 겹쳐 올린다. */
  const isLanding = useLocation().pathname === '/';

  const handleLogout = async () => {
    try {
      await logout();
    } finally {
      clear();
      navigate('/');
    }
  };

  /* 랜딩은 워드마크만 남긴다. 검색·장바구니·로그인은 첫 화면의 서사를 흐리고,
     진입 동선은 화면 가운데의 Get started 하나로 모은다. */
  if (isLanding) {
    return (
      <header className="bb-header bb-header--overlay">
        <div className="bb-header__bar bb-header__bar--landing">
          <Link to="/" className="bb-header__logo" aria-label="뷰티보이 홈으로">
            BEAUTY BOY<span className="bb-header__logo-dot">.</span>
          </Link>

          {/* 해당 화면들은 후속 웨이브 범위 — 기존 헤더의 "준비 중" 항목과 같이 자리만 잡는다 */}
          <nav className="bb-landing-nav" aria-label="주요 메뉴(준비 중)">
            {LANDING_NAV.map((label) => (
              <span className="bb-landing-nav__item" key={label}>
                {label}
              </span>
            ))}
          </nav>
        </div>
      </header>
    );
  }

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
          {isBootstrapping ? (
            <span className="bb-header__auth-skeleton" aria-hidden="true" />
          ) : member ? (
            <>
              <span className="bb-header__nav-link bb-header__nav-link--member" aria-label="로그인됨">
                {member.nickname}님
              </span>
              <button type="button" className="bb-header__logout" onClick={handleLogout}>
                로그아웃
              </button>
            </>
          ) : (
            <Link to="/login" className="bb-header__nav-link">
              로그인
            </Link>
          )}
        </nav>
      </div>
    </header>
  );
}

import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import './Header.css';
import { useAuthStore } from '../../stores/authStore';
import { logout } from '../../api/auth';
import { fetchCartItems } from '../../api/cart';

/* 랜딩 내비의 실제 IA. 라벨은 전부 한글이다 — DESIGN.md "영문/한글 혼용 규칙"이 영문을
   아이브로우·배지/워드마크 두 자리로만 한정한다(내비 라벨은 그 밖의 UI 문구).
   비로그인으로 /routine 외의 항목을 누르면 RequireAuth가 /login으로 보내는데, 이는
   랜딩의 Get started와 같은 종착이라 의도된 퍼널이다. */
const LANDING_NAV = [
  { label: '루틴 가이드', to: '/routine' },
  { label: '랭킹', to: '/ranking' },
  { label: '전체 상품', to: '/goods' },
  { label: '로그인', to: '/login' },
] as const;

/* 앱(로그인 이후) 헤더의 주요 메뉴 — 랜딩 내비의 로그인 링크만 뺀 3개.
   로그인/로그아웃은 이 배열과 별도로 기존 자리(cart 옆)에 그대로 둔다. */
const PRIMARY_NAV = [
  { label: '루틴 가이드', to: '/routine' },
  { label: '랭킹', to: '/ranking' },
  { label: '전체 상품', to: '/goods' },
] as const;

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
  const pathname = useLocation().pathname;
  const isLanding = pathname === '/';
  /* 로그인·가입은 좌우 스플릿(왼쪽 검정 브랜드 패널)이 자체 워드마크를 가진다 —
     공용 헤더를 얹으면 스플릿 상단이 잘리므로 이 화면들에서는 헤더를 렌더하지 않는다. */
  const isAuth = pathname === '/login' || pathname === '/signup';

  /*
   * queryKey는 Cart.tsx:27과 반드시 같은 ['cart']를 쓴다 — 장바구니 화면의 수량 변경·삭제가
   * 부르는 invalidateQueries({ queryKey: ['cart'] })(Cart.tsx:45,51)가 이 쿼리도 함께
   * 무효화해, 담기·삭제 시 헤더 배지가 별도 배선 없이 따라 갱신된다.
   *
   * enabled: !!member — GET /cart/items는 인증이 필요하다. 비로그인에 그냥 쏘면 401 →
   * 리프레시 인터셉터가 매 페이지에서 불필요한 왕복을 만든다. 비로그인 사용자에게는
   * 아예 요청을 보내지 않는다.
   */
  const cartQuery = useQuery({
    queryKey: ['cart'],
    queryFn: fetchCartItems,
    enabled: !!member,
  });
  // 개수의 정의: 라인 수(items.length) — 장바구니 화면이 나열하는 줄 수와 같은 값이다.
  // 수량 합계가 아니다(수량 2개인 상품 1줄이면 배지는 1). Cart.tsx의 items.map과 동일한 단위로
  // 맞춰야 "헤더 배지 = 장바구니에서 보이는 줄 수"라는 사용자 기대가 어긋나지 않는다.
  const cartCount = cartQuery.data?.length;

  const handleLogout = async () => {
    try {
      await logout();
    } finally {
      clear();
      navigate('/');
    }
  };

  if (isAuth) {
    return null;
  }

  /* 랜딩은 워드마크만 남긴다. 검색·장바구니·로그인은 첫 화면의 서사를 흐리고,
     진입 동선은 화면 가운데의 Get started 하나로 모은다. */
  if (isLanding) {
    return (
      <header className="bb-header bb-header--overlay">
        <div className="bb-header__bar bb-header__bar--landing">
          {/* 랜딩은 히어로 정중앙의 거대 워드마크가 이미 브랜드명을 말한다 —
              좌상단에는 아무것도 두지 않고 우측 내비만 남긴다. */}
          <nav className="bb-landing-nav" aria-label="주요 메뉴">
            {LANDING_NAV.map(({ label, to }) => (
              <Link to={to} className="bb-landing-nav__item" key={to}>
                {label}
              </Link>
            ))}
          </nav>
        </div>
      </header>
    );
  }

  return (
    <header className="bb-header bb-header--dark">
      <div className="bb-header__bar">
        <Link to="/main" className="bb-header__logo" aria-label="뷰티보이 메인으로">
          BEAUTY BOY
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
          {/* 주요 메뉴는 장바구니·계정과 같은 우측 그룹에 둔다 — 상단바의 조작 지점을
              한쪽으로 모으고, 좁은 화면에서는 이 묶음만 접는다. */}
          <div className="bb-header__primary-nav">
            {PRIMARY_NAV.map(({ label, to }) => (
              <Link
                to={to}
                className="bb-header__nav-link"
                key={to}
                aria-current={pathname === to ? 'page' : undefined}
              >
                {label}
              </Link>
            ))}
          </div>

          <Link to="/cart" className="bb-header__nav-link" aria-label="장바구니">
            장바구니
            {/* 로딩 중(cartCount === undefined)에는 배지를 그리지 않는다 — 하드코드 0을 그려
                실제 개수와 어긋나던 이전 결함을 반복하지 않기 위해서다. 데이터가 도착한 뒤에는
                0이어도 그린다 — 로그인 상태의 실제 장바구니 상태(비어 있음)를 숨기지 않는다. */}
            {member && cartCount !== undefined && (
              <span className="bb-header__cart-count">{cartCount}</span>
            )}
          </Link>
          {isBootstrapping ? (
            <span className="bb-header__auth-skeleton" aria-hidden="true" />
          ) : member ? (
            <>
              <Link to="/mypage" className="bb-header__nav-link bb-header__nav-link--member">
                {member.nickname}님
              </Link>
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

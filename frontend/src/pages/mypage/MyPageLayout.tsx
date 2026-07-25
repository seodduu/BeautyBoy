import { NavLink, Outlet } from 'react-router-dom';
import './MyPageLayout.css';

const TABS: { to: string; label: string }[] = [
  { to: 'orders', label: '주문내역' },
  { to: 'wishlist', label: '찜' },
  { to: 'reviews', label: '내 리뷰' },
  { to: 'profile', label: '프로필' },
];

/**
 * 마이페이지 `/mypage/*` 뼈대 — 좌측(모바일은 상단 가로 스크롤) 탭 네비 + `<Outlet />`.
 * `NavLink`는 기본이 prefix 매칭(`end` 미지정)이라 `/mypage/orders/:orderNo` 상세 화면에서도
 * "주문내역" 탭이 계속 활성 상태로 남는다 — 주문 상세는 주문내역 탭의 하위 화면이라 맞는 동작이다.
 */
export function MyPageLayout() {
  return (
    <div className="bb-mypage">
      <h1 className="bb-mypage__title">마이페이지</h1>
      <div className="bb-mypage__body">
        <nav className="bb-mypage__nav" aria-label="마이페이지 메뉴">
          {TABS.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              className={({ isActive }) =>
                `bb-mypage__nav-link${isActive ? ' bb-mypage__nav-link--active' : ''}`
              }
            >
              {tab.label}
            </NavLink>
          ))}
        </nav>
        <div className="bb-mypage__content">
          <Outlet />
        </div>
      </div>
    </div>
  );
}

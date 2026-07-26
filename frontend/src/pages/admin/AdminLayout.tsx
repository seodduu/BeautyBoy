import { NavLink, Outlet } from 'react-router-dom';
import './AdminLayout.css';

const TABS: { to: string; label: string }[] = [
  { to: 'goods', label: '상품' },
  { to: 'routine', label: '루틴' },
  { to: 'qna', label: '문의' },
];

/**
 * 관리자 뼈대 `/admin/*` — MyPageLayout과 같은 뼈대(좌측 탭 네비 + `<Outlet />`)를 재사용한다.
 * 실제 접근 제어는 이 컴포넌트가 아니라 router.tsx에서 이 레이아웃을 감싸는 RequireAdmin +
 * 서버의 `@PreAuthorize("hasRole('ADMIN')")`가 한다 — 여기서는 화면 뼈대만 책임진다.
 */
export function AdminLayout() {
  return (
    <div className="bb-admin">
      <h1 className="bb-admin__title">관리자</h1>
      <div className="bb-admin__body">
        <nav className="bb-admin__nav" aria-label="관리자 메뉴">
          {TABS.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              className={({ isActive }) =>
                `bb-admin__nav-link${isActive ? ' bb-admin__nav-link--active' : ''}`
              }
            >
              {tab.label}
            </NavLink>
          ))}
        </nav>
        <div className="bb-admin__content">
          <Outlet />
        </div>
      </div>
    </div>
  );
}

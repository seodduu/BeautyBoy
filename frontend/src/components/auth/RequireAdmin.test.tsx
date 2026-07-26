import { afterEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';
import { RequireAdmin } from './RequireAdmin';

function renderAdminRoute() {
  return render(
    <MemoryRouter initialEntries={['/admin/goods']}>
      <Routes>
        <Route
          path="/admin/goods"
          element={
            <RequireAdmin>
              <h1>상품 관리</h1>
            </RequireAdmin>
          }
        />
        <Route path="/main" element={<p>뷰티보이 메인</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => {
  useAuthStore.getState().clear();
  useAuthStore.getState().setBootstrapping(false);
});

describe('RequireAdmin — 관리자 라우트 가드', () => {
  it('role이 ADMIN이 아니면 메인으로 돌려보낸다', async () => {
    useAuthStore.getState().setBootstrapping(false);
    useAuthStore.getState().setAuth('token', {
      id: 1,
      email: 'user@beautyboy.dev',
      nickname: '민수',
      grade: 'BRONZE',
      role: 'USER',
    });

    renderAdminRoute();

    expect(await screen.findByText('뷰티보이 메인')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '상품 관리' })).not.toBeInTheDocument();
  });

  it('비로그인(member 없음)이면 메인으로 돌려보낸다', async () => {
    useAuthStore.getState().setBootstrapping(false);

    renderAdminRoute();

    expect(await screen.findByText('뷰티보이 메인')).toBeInTheDocument();
  });

  it('role이 ADMIN이면 children을 그대로 렌더한다', () => {
    useAuthStore.getState().setBootstrapping(false);
    useAuthStore.getState().setAuth('token', {
      id: 2,
      email: 'admin@beautyboy.dev',
      nickname: '관리자',
      grade: 'BRONZE',
      role: 'ADMIN',
    });

    renderAdminRoute();

    expect(screen.getByRole('heading', { name: '상품 관리' })).toBeInTheDocument();
  });

  it('isBootstrapping 동안에는 리다이렉트하지 않고 대기 안내를 보여준다', () => {
    useAuthStore.getState().setBootstrapping(true);

    renderAdminRoute();

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText('뷰티보이 메인')).not.toBeInTheDocument();
  });
});

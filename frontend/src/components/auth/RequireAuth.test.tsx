import { afterEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './RequireAuth';
import { useAuthStore } from '../../stores/authStore';

function renderGuarded() {
  return render(
    <MemoryRouter initialEntries={['/main']}>
      <Routes>
        <Route
          path="/main"
          element={
            <RequireAuth>
              <div>MAIN_MARKER</div>
            </RequireAuth>
          }
        />
        <Route path="/login" element={<div>LOGIN_MARKER</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('RequireAuth — 보호 라우트 가드', () => {
  afterEach(() => {
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
  });

  it('부트스트랩이 끝난 비로그인 상태면 /login으로 보낸다', () => {
    useAuthStore.setState({ accessToken: null, isBootstrapping: false });

    renderGuarded();

    expect(screen.getByText('LOGIN_MARKER')).toBeInTheDocument();
    expect(screen.queryByText('MAIN_MARKER')).toBeNull();
  });

  it('로그인 상태면 자식을 그대로 렌더한다', () => {
    useAuthStore.setState({ accessToken: 'token', isBootstrapping: false });

    renderGuarded();

    expect(screen.getByText('MAIN_MARKER')).toBeInTheDocument();
  });

  it('부트스트랩 중에는 판정을 미루고 /login으로 보내지 않는다(새로고침 회귀 방지)', () => {
    useAuthStore.setState({ accessToken: null, isBootstrapping: true });

    renderGuarded();

    expect(screen.queryByText('LOGIN_MARKER')).toBeNull();
    expect(screen.queryByText('MAIN_MARKER')).toBeNull();
    // 대기 중임을 스크린리더에도 알린다.
    expect(screen.getByRole('status')).toHaveTextContent('로그인 상태를 확인하는 중입니다');
  });
});

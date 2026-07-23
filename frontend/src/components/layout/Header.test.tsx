import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { Header } from './Header';
import { useAuthStore } from '../../stores/authStore';

function renderHeader() {
  return render(
    <MemoryRouter initialEntries={['/mypage']}>
      <Routes>
        <Route path="/mypage" element={<Header />} />
        <Route path="/" element={<div>HOME_MARKER</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Header — 로그인/로그아웃 UI', () => {
  beforeEach(() => {
    // 부트스트랩이 끝난 상태를 기본값으로 둔다 — 이 테스트들은 로그인/로그아웃 결과 UI만 본다.
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: false });
  });

  it('비로그인 상태에서는 "로그인" 링크가 보이고 로그아웃 버튼은 없다', () => {
    renderHeader();

    expect(screen.getByRole('link', { name: '로그인' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument();
  });

  it('로그인 상태에서는 로그아웃 버튼이 보이고, 클릭 시 서버에 로그아웃 요청 후 스토어가 비워지고 홈으로 이동한다', async () => {
    useAuthStore.getState().setAuth('token-abc', {
      id: 1,
      email: 'test@beautyboy.dev',
      nickname: '민수',
      grade: 'BRONZE',
    });

    let logoutCalled = false;
    server.use(
      http.post('/api/v1/auth/logout', () => {
        logoutCalled = true;
        return HttpResponse.json({ code: 'OK', message: '성공', data: null });
      }),
    );

    renderHeader();

    expect(screen.getByText('민수님')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '로그인' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }));

    await waitFor(() => expect(logoutCalled).toBe(true));
    await screen.findByText('HOME_MARKER');

    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useAuthStore.getState().member).toBeNull();
  });

  it('부트스트랩 진행 중에는 로그인/로그아웃 링크 대신 스켈레톤을 보여준다', () => {
    useAuthStore.setState({ isBootstrapping: true });

    renderHeader();

    expect(screen.queryByRole('link', { name: '로그인' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument();
  });
});

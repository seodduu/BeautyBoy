import { afterEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Login } from './Login';
import { useAuthStore } from '../stores/authStore';

describe('Login — 성공 후 이동지', () => {
  afterEach(() => {
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
  });

  it('로그인에 성공하면 랜딩이 아니라 /main으로 간다', async () => {
    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/main" element={<div>MAIN_MARKER</div>} />
          <Route path="/" element={<div>LANDING_MARKER</div>} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'mock@beautyboy.dev' },
    });
    fireEvent.change(screen.getByLabelText('비밀번호'), {
      target: { value: 'password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => expect(screen.getByText('MAIN_MARKER')).toBeInTheDocument());
    expect(screen.queryByText('LANDING_MARKER')).toBeNull();
  });
});

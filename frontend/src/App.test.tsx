import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from './mocks/server';
import App from './App';
import { useAuthStore } from './stores/authStore';

describe('App — 부트스트랩 세션 복원', () => {
  afterEach(() => {
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
  });

  it('/auth/refresh가 200(accessToken+member)이면 렌더 후 헤더에 닉네임이 나타난다', async () => {
    server.use(
      http.post('/api/v1/auth/refresh', () =>
        HttpResponse.json({
          code: 'OK',
          message: '성공',
          data: {
            accessToken: 'restored-token',
            member: { id: 1, email: 'a@beautyboy.dev', nickname: '영희', grade: 'BRONZE' },
          },
        }),
      ),
    );

    render(<App />);

    expect(await screen.findByText('영희님')).toBeInTheDocument();
    expect(useAuthStore.getState().accessToken).toBe('restored-token');
  });

  it('/auth/refresh가 401이면 "로그인" 링크가 유지되고, 콘솔 에러나 추가 재시도가 없다(재귀 방지 회귀)', async () => {
    let refreshCalls = 0;
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    server.use(
      http.post('/api/v1/auth/refresh', () => {
        refreshCalls++;
        return new HttpResponse(null, { status: 401 });
      }),
    );

    render(<App />);

    expect(await screen.findByRole('link', { name: '로그인' })).toBeInTheDocument();
    await waitFor(() => expect(refreshCalls).toBe(1));

    // 인터셉터가 refresh 실패에 반응해 추가로 refresh를 부르지 않는지 확인 — 시간을 조금 더 준다.
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(refreshCalls).toBe(1);
    expect(consoleErrorSpy).not.toHaveBeenCalled();

    consoleErrorSpy.mockRestore();
  });
});

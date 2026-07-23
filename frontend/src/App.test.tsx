import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from './mocks/server';
import App from './App';
import { useAuthStore } from './stores/authStore';

describe('App — 부트스트랩 세션 복원', () => {
  /* 이 스위트의 주제는 "부트스트랩이 세션을 복원하는가"이지 헤더의 생김새가 아니다.
     랜딩(/)의 헤더는 워드마크만 남기고 인증 UI를 두지 않으므로(진입은 가운데 CTA 하나로 모은다)
     복원 결과는 DOM 대신 스토어 상태로 확인한다.
     헤더가 인증 상태를 어떻게 그리는지는 Header.test.tsx가 /mypage에서 따로 검증한다. */
  afterEach(() => {
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
  });

  it('/auth/refresh가 200(accessToken+member)이면 렌더 후 세션이 복원된다', async () => {
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

    await waitFor(() => expect(useAuthStore.getState().member?.nickname).toBe('영희'));
    expect(useAuthStore.getState().accessToken).toBe('restored-token');
    expect(useAuthStore.getState().isBootstrapping).toBe(false);
  });

  it('/auth/refresh가 401이면 비로그인으로 남고, 콘솔 에러나 추가 재시도가 없다(재귀 방지 회귀)', async () => {
    let refreshCalls = 0;
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    server.use(
      http.post('/api/v1/auth/refresh', () => {
        refreshCalls++;
        return new HttpResponse(null, { status: 401 });
      }),
    );

    render(<App />);

    await waitFor(() => expect(useAuthStore.getState().isBootstrapping).toBe(false));
    expect(useAuthStore.getState().member).toBeNull();
    expect(useAuthStore.getState().accessToken).toBeNull();
    await waitFor(() => expect(refreshCalls).toBe(1));

    // 인터셉터가 refresh 실패에 반응해 추가로 refresh를 부르지 않는지 확인 — 시간을 조금 더 준다.
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(refreshCalls).toBe(1);
    expect(consoleErrorSpy).not.toHaveBeenCalled();

    consoleErrorSpy.mockRestore();
  });
});

import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from './mocks/server';
import App from './App';
import { useAuthStore } from './stores/authStore';
import { refreshSession } from './api/auth';

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

  it('/auth/refresh가 409(동시 리프레시 패배)면 이미 복구된 세션을 지우지 않는다', async () => {
    // 서버는 이제 동시 refresh의 패배자에게 409를 준다(AUTH_REFRESH_CONFLICT).
    // 409는 "인증 실패"가 아니라 "다른 요청이 먼저 토큰을 가져갔다"이므로, 그 시점에 세션을
    // 비우면 승자가 방금 정상 발급한 로그인 상태까지 날아간다 — 그것이 Task 4-16a의 결함이다.
    useAuthStore.setState({
      accessToken: '승자가-발급한-토큰',
      member: { id: 1, email: 'a@beautyboy.dev', nickname: '영희', grade: 'BRONZE' },
    });

    server.use(
      http.post('/api/v1/auth/refresh', () =>
        HttpResponse.json(
          { code: 'AUTH_REFRESH_CONFLICT', message: '리프레시 토큰이 교체되었습니다', data: null },
          { status: 409 },
        ),
      ),
    );

    render(<App />);

    await waitFor(() => expect(useAuthStore.getState().isBootstrapping).toBe(false));
    expect(useAuthStore.getState().accessToken).toBe('승자가-발급한-토큰');
    expect(useAuthStore.getState().member?.nickname).toBe('영희');
  });

  it('/auth/refresh가 500이어도 이미 복구된 세션을 지우지 않는다', async () => {
    useAuthStore.setState({
      accessToken: '승자가-발급한-토큰',
      member: { id: 1, email: 'a@beautyboy.dev', nickname: '영희', grade: 'BRONZE' },
    });

    server.use(http.post('/api/v1/auth/refresh', () => new HttpResponse(null, { status: 500 })));

    render(<App />);

    await waitFor(() => expect(useAuthStore.getState().isBootstrapping).toBe(false));
    expect(useAuthStore.getState().accessToken).toBe('승자가-발급한-토큰');
  });
});

describe('refreshSession — 부트스트랩 refresh의 in-flight 공유', () => {
  afterEach(() => {
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
  });

  it('동시에 두 번 불러도 서버로 나가는 요청은 1건이고 둘 다 같은 결과를 받는다', async () => {
    // 리프레시 토큰은 한 번 쓰면 회전하므로, 같은 토큰으로 두 요청을 동시에 보내면 서버가
    // 한쪽을 409로 돌려보낸다(정상 계약). StrictMode 이중 호출에서 살아남는 쪽이 하필
    // 패배자면 유효한 쿠키가 있는데도 /login으로 튕긴다 — 애초에 요청을 하나로 합쳐 막는다.
    let refreshCalls = 0;
    server.use(
      http.post('/api/v1/auth/refresh', async () => {
        refreshCalls++;
        await new Promise((resolve) => setTimeout(resolve, 20));
        return HttpResponse.json({
          code: 'OK',
          message: '성공',
          data: {
            accessToken: 'shared-token',
            member: { id: 1, email: 'a@beautyboy.dev', nickname: '영희', grade: 'BRONZE' },
          },
        });
      }),
    );

    const [first, second] = await Promise.all([refreshSession(), refreshSession()]);

    expect(refreshCalls).toBe(1);
    expect(first.accessToken).toBe('shared-token');
    expect(second.accessToken).toBe('shared-token');

    // 완료 후에는 공유가 해제되어 다음 부트스트랩이 새로 요청한다(세션이 영원히 굳지 않는다).
    await refreshSession();
    expect(refreshCalls).toBe(2);
  });
});

import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from './mocks/server';
import App, { queryClient } from './App';
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

  it('/auth/refresh가 401이면 세션을 비우고, 콘솔 에러나 추가 재시도가 없다(재귀 방지 회귀)', async () => {
    let refreshCalls = 0;
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    // 스토어를 미리 채운 뒤 시작한다. 비어 있는 상태로 시작하면 세션이 비워지지 않아도 단언이
    // 통과해버려서 "401이면 세션이 사라진다"가 통째로 미검증으로 남는다.
    //
    // 다만 이 단언이 App.tsx의 isSessionGone() 분기를 '단독으로' 못 박지는 못한다:
    // 동결 파일 client.ts의 응답 인터셉터가 refresh 요청의 401에 대해 이미 clear()를 부르기
    // 때문에(client.ts:63-67), App.tsx 쪽 clear()를 없애도 결과는 같다(변이 테스트로 확인함).
    // 즉 App.tsx의 401 clear()는 인터셉터와 중복인 방어층이다. 여기서 지키는 것은
    // "401이면 세션이 사라진다"라는 사용자 관점 계약이고, 그 반대편(409·5xx에는 사라지지 않는다)은
    // 아래 두 테스트가 덮는다 — 그쪽은 인터셉터가 관여하지 않으므로 App.tsx 분기를 단독으로 검증한다.
    useAuthStore.setState({
      accessToken: '낡은-토큰',
      member: { id: 1, email: 'a@beautyboy.dev', nickname: '영희', grade: 'BRONZE' },
    });

    server.use(
      http.post('/api/v1/auth/refresh', () => {
        refreshCalls++;
        return new HttpResponse(null, { status: 401 });
      }),
    );

    render(<App />);

    await waitFor(() => expect(useAuthStore.getState().isBootstrapping).toBe(false));
    await waitFor(() => expect(useAuthStore.getState().accessToken).toBeNull());
    expect(useAuthStore.getState().member).toBeNull();
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

describe('QueryClient 기본값', () => {
  it('staleTime이 60초다 — 탭 전환마다 재요청하지 않는다', () => {
    expect(queryClient.getDefaultOptions().queries?.staleTime).toBe(60_000);
  });

  it('refetchOnWindowFocus가 꺼져 있다', () => {
    expect(queryClient.getDefaultOptions().queries?.refetchOnWindowFocus).toBe(false);
  });

  it('쿼리 재시도는 1회다 — 기본 3회는 오류 화면이 뜨기까지 너무 오래 걸린다', () => {
    expect(queryClient.getDefaultOptions().queries?.retry).toBe(1);
  });

  it('뮤테이션은 재시도하지 않는다 — 담기·주문의 중복 실행은 사고다', () => {
    expect(queryClient.getDefaultOptions().mutations?.retry).toBe(0);
  });
});

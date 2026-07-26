import { beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { api } from './client';
import { refreshSession } from './auth';
import { useAuthStore } from '../stores/authStore';

describe('api 클라이언트 — 401 리프레시 인터셉터', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it('401이면 refresh 후 원요청을 재시도한다', async () => {
    let firstCall = 0;
    server.use(
      http.get('/api/v1/members/me', () =>
        firstCall++ === 0
          ? new HttpResponse(null, { status: 401 })
          : HttpResponse.json({ code: 'OK', data: { nickname: '민수' } }),
      ),
      http.post('/api/v1/auth/refresh', () =>
        HttpResponse.json({ code: 'OK', data: { accessToken: 'new-token' } }),
      ),
    );

    const res = await api.get('/members/me');

    expect(res.data.data.nickname).toBe('민수');
    expect(useAuthStore.getState().accessToken).toBe('new-token');
  });

  it('refresh 요청 자체가 401이면 재시도 없이 스토어를 비운다 (무한루프 방지)', async () => {
    useAuthStore.getState().setAuth('stale-token');
    let refreshCalls = 0;

    server.use(
      http.get('/api/v1/members/me', () => new HttpResponse(null, { status: 401 })),
      http.post('/api/v1/auth/refresh', () => {
        refreshCalls++;
        return new HttpResponse(null, { status: 401 });
      }),
    );

    await expect(api.get('/members/me')).rejects.toBeTruthy();

    expect(refreshCalls).toBe(1);
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it('동시에 여러 요청이 401을 받아도 refresh는 한 번만 호출된다', async () => {
    let meCalls = 0;
    let refreshCalls = 0;

    server.use(
      http.get('/api/v1/members/me', () =>
        meCalls++ < 2
          ? new HttpResponse(null, { status: 401 })
          : HttpResponse.json({ code: 'OK', data: { nickname: '민수' } }),
      ),
      http.post('/api/v1/auth/refresh', () => {
        refreshCalls++;
        return HttpResponse.json({ code: 'OK', data: { accessToken: 'new-token' } });
      }),
    );

    const [res1, res2] = await Promise.all([api.get('/members/me'), api.get('/members/me')]);

    expect(res1.data.data.nickname).toBe('민수');
    expect(res2.data.data.nickname).toBe('민수');
    expect(refreshCalls).toBe(1);
  });

  it('리프레시가 409(동시 경합)로 실패해도 스토어를 비우지 않는다', async () => {
    useAuthStore.setState({
      accessToken: '살아있는-토큰',
      member: { id: 1, email: 'a@beautyboy.dev', nickname: '영희', grade: 'BRONZE' },
    });
    let refreshCalls = 0;

    server.use(
      http.get('/api/v1/members/me', () => new HttpResponse(null, { status: 401 })),
      http.post('/api/v1/auth/refresh', () => {
        refreshCalls++;
        return HttpResponse.json(
          { code: 'AUTH_REFRESH_CONFLICT', message: '리프레시 토큰이 교체되었습니다', data: null },
          { status: 409 },
        );
      }),
    );

    await expect(api.get('/members/me')).rejects.toBeTruthy();

    expect(refreshCalls).toBe(1);
    // 409는 "인증 실패"가 아니라 "경합에서 졌다"이므로 살아있는 세션을 지우면 안 된다.
    expect(useAuthStore.getState().accessToken).toBe('살아있는-토큰');
  });

  it('인터셉터 리프레시와 부트스트랩 refreshSession이 동시에 일어나도 서버 요청은 1회만 나간다', async () => {
    let refreshCalls = 0;

    server.use(
      http.get('/api/v1/members/me', () => new HttpResponse(null, { status: 401 })),
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

    // 인터셉터 경로(api.get 401 → refresh)와 부트스트랩 경로(refreshSession 직접 호출)를
    // 동시에 발동시킨다 — 두 소비자가 같은 in-flight promise를 공유해야 refresh가 1회만 나간다.
    const [, sessionResult] = await Promise.all([
      api.get('/members/me').catch(() => null),
      refreshSession(),
    ]);

    expect(refreshCalls).toBe(1);
    expect(sessionResult.accessToken).toBe('shared-token');
  });
});

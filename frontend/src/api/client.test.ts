import { beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { api } from './client';
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
});

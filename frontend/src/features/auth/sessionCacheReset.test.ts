import { QueryClient } from '@tanstack/react-query';
import { afterEach, describe, expect, it } from 'vitest';
import { useAuthStore } from '../../stores/authStore';
import { installSessionCacheReset } from './sessionCacheReset';

/**
 * 각 테스트가 끝나면 store를 초기 상태로 되돌린다 — 테스트끼리 member/accessToken을
 * 공유하는 zustand 싱글턴이므로, 여기서 안 지우면 실행 순서에 따라 다른 테스트가 오염된다.
 */
afterEach(() => {
  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
});

describe('installSessionCacheReset', () => {
  it('로그아웃하면 캐시를 비운다', () => {
    const queryClient = new QueryClient();
    useAuthStore.setState({ member: { id: 1, email: 'a@test.dev', nickname: 'a', grade: 'BABY' } });
    queryClient.setQueryData(['cart'], [{ id: 1 }]);

    const unsubscribe = installSessionCacheReset(queryClient);

    useAuthStore.getState().clear();

    expect(queryClient.getQueryData(['cart'])).toBeUndefined();

    unsubscribe();
  });

  it('계정이 바뀌면 캐시를 비운다', () => {
    const queryClient = new QueryClient();
    useAuthStore.setState({ member: { id: 1, email: 'a@test.dev', nickname: 'a', grade: 'BABY' } });
    queryClient.setQueryData(['cart'], [{ id: 1 }]);

    const unsubscribe = installSessionCacheReset(queryClient);

    useAuthStore.getState().setAuth('token-b', { id: 2, email: 'b@test.dev', nickname: 'b', grade: 'BABY' });

    expect(queryClient.getQueryData(['cart'])).toBeUndefined();

    unsubscribe();
  });

  it('최초 로그인은 캐시를 비우지 않는다', () => {
    const queryClient = new QueryClient();
    useAuthStore.setState({ member: null });
    queryClient.setQueryData(['ranking'], [{ id: 1 }]);

    const unsubscribe = installSessionCacheReset(queryClient);

    useAuthStore.getState().setAuth('token-a', { id: 1, email: 'a@test.dev', nickname: 'a', grade: 'BABY' });

    expect(queryClient.getQueryData(['ranking'])).toEqual([{ id: 1 }]);

    unsubscribe();
  });

  it('같은 계정의 토큰 갱신은 캐시를 비우지 않는다', () => {
    const queryClient = new QueryClient();
    useAuthStore.setState({ member: { id: 1, email: 'a@test.dev', nickname: 'a', grade: 'BABY' } });
    queryClient.setQueryData(['cart'], [{ id: 1 }]);

    const unsubscribe = installSessionCacheReset(queryClient);

    useAuthStore.getState().setAuth('token-refreshed', { id: 1, email: 'a@test.dev', nickname: 'a', grade: 'BABY' });

    expect(queryClient.getQueryData(['cart'])).toEqual([{ id: 1 }]);

    unsubscribe();
  });
});

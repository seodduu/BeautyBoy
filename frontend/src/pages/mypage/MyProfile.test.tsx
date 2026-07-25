import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { ToastProvider } from '../../components/ui/ToastProvider';
import { MyProfile } from './MyProfile';
import * as memberApi from '../../api/member';
import type { Address, Me } from '../../api/member';

function envelope<T>(data: T) {
  return { code: 'OK', message: 'success', data };
}

const ME: Me = {
  id: 1,
  email: 'test@beautyboy.dev',
  nickname: '테스터',
  grade: 'BRONZE',
  skinType: null,
  concerns: [],
  ageBand: null,
};

const ADDRESSES: Address[] = [
  {
    id: 1,
    receiver: '김민수',
    phone: '01012345678',
    zipcode: '06236',
    address1: '서울특별시 강남구 테헤란로 1',
    address2: '101동 202호',
    isDefault: true,
  },
];

function registerHandlers() {
  server.use(
    http.get('/api/v1/members/me', () => HttpResponse.json(envelope(ME))),
    http.get('/api/v1/members/me/addresses', () => HttpResponse.json(envelope(ADDRESSES))),
    http.put('/api/v1/members/me/profile', async ({ request }) => {
      await request.json();
      return HttpResponse.json(envelope(null));
    }),
  );
}

function renderMyProfile() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/mypage/profile']}>
          <MyProfile />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
});

describe('MyProfile — 마이페이지 프로필', () => {
  it('프로필 저장은 PUT /members/me/profile을 부르고 성공 토스트를 띄운다', async () => {
    registerHandlers();
    const updateProfileSpy = vi.spyOn(memberApi, 'updateProfile');

    renderMyProfile();

    fireEvent.click(await screen.findByRole('radio', { name: '건성' }));
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() =>
      expect(updateProfileSpy).toHaveBeenCalledWith(expect.objectContaining({ skinType: 'DRY' })),
    );
    expect(await screen.findByText('프로필을 저장했어요')).toBeInTheDocument();

    updateProfileSpy.mockRestore();
  });
});

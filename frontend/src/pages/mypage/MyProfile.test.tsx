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

  it('배송지 수정은 바뀐 값으로 PUT /members/me/addresses/{id}를 부른다', async () => {
    registerHandlers();
    const updateAddressSpy = vi.spyOn(memberApi, 'updateAddress');

    renderMyProfile();

    fireEvent.click(await screen.findByRole('button', { name: '수정' }));

    // 값을 실제로 바꾼다 — 폼이 그대로 다시 전송되는 게 아니라 사용자가 고친 값이
    // 전달되는지를 증명해야 한다(목 호출 횟수만 세는 것으로는 부족).
    fireEvent.change(screen.getByLabelText('받는 분'), { target: { value: '이영희' } });
    fireEvent.change(screen.getByLabelText('연락처'), { target: { value: '01099998888' } });

    // "저장" 버튼이 화면에 둘(피부 프로필 저장 · 배송지 수정 저장) 있으므로 마지막(배송지
    // 수정 폼) 것을 누른다 — 배송지 수정 폼이 DOM상 프로필 저장 버튼보다 뒤에 있다.
    const saveButtons = screen.getAllByRole('button', { name: '저장' });
    fireEvent.click(saveButtons[saveButtons.length - 1]);

    await waitFor(() =>
      expect(updateAddressSpy).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          receiver: '이영희',
          phone: '01099998888',
          zipcode: '06236',
          address1: '서울특별시 강남구 테헤란로 1',
          address2: '101동 202호',
          isDefault: true,
        }),
      ),
    );
    expect(await screen.findByText('배송지를 수정했어요')).toBeInTheDocument();

    updateAddressSpy.mockRestore();
  });
});

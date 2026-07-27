import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { Signup } from './Signup';
import { useAuthStore } from '../stores/authStore';

function renderSignup() {
  return render(
    <MemoryRouter initialEntries={['/signup']}>
      <Routes>
        <Route path="/signup" element={<Signup />} />
        <Route path="/" element={<div>HOME_MARKER</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function fillAccountStep() {
  fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'test@beautyboy.dev' } });
  fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password123' } });
  fireEvent.change(screen.getByLabelText('닉네임'), { target: { value: '민수' } });
  fireEvent.click(screen.getByRole('button', { name: '다음' }));
}

describe('Signup — 2스텝 가입 흐름', () => {
  it('"건너뛰기" 클릭 시 signup 요청 body에 skinType이 없다', async () => {
    let capturedBody: Record<string, unknown> | null = null;

    server.use(
      http.post('/api/v1/auth/signup', async ({ request }) => {
        capturedBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          { code: 'OK', message: '성공', data: { id: 1, email: 'test@beautyboy.dev', nickname: '민수', grade: 'BRONZE' } },
          { status: 201 },
        );
      }),
      http.post('/api/v1/auth/login', () =>
        HttpResponse.json({ code: 'OK', message: '성공', data: { accessToken: 'token-abc' } }),
      ),
      http.get('/api/v1/members/me', () =>
        HttpResponse.json({
          code: 'OK',
          message: '성공',
          data: { id: 1, email: 'test@beautyboy.dev', nickname: '민수', grade: 'BRONZE' },
        }),
      ),
    );

    renderSignup();

    fillAccountStep();

    // 2스텝: 피부 프로필 — 건너뛰기
    expect(await screen.findByText('피부 프로필')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '건너뛰기' }));

    await waitFor(() => {
      expect(capturedBody).not.toBeNull();
    });

    expect(capturedBody).not.toBeNull();
    expect('skinType' in capturedBody!).toBe(false);
    expect(capturedBody).toMatchObject({
      email: 'test@beautyboy.dev',
      password: 'password123',
      nickname: '민수',
    });

    // 가입 후 자동 로그인 + 홈 이동까지 확인
    await screen.findByText('HOME_MARKER');
    expect(useAuthStore.getState().accessToken).toBe('token-abc');
    expect(useAuthStore.getState().member?.nickname).toBe('민수');
  });

  it('피부 프로필을 채우고 "가입 완료"하면 skinType·concerns·ageBand가 body에 포함된다', async () => {
    let capturedBody: Record<string, unknown> | null = null;

    server.use(
      http.post('/api/v1/auth/signup', async ({ request }) => {
        capturedBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          { code: 'OK', message: '성공', data: { id: 2, email: 'skin@beautyboy.dev', nickname: '철수', grade: 'BRONZE' } },
          { status: 201 },
        );
      }),
      http.post('/api/v1/auth/login', () =>
        HttpResponse.json({ code: 'OK', message: '성공', data: { accessToken: 'token-xyz' } }),
      ),
      http.get('/api/v1/members/me', () =>
        HttpResponse.json({
          code: 'OK',
          message: '성공',
          data: { id: 2, email: 'skin@beautyboy.dev', nickname: '철수', grade: 'BRONZE' },
        }),
      ),
    );

    renderSignup();
    fillAccountStep();

    await screen.findByText('피부 프로필');
    fireEvent.click(screen.getByText('지성'));
    fireEvent.click(screen.getByRole('button', { name: '모공' }));
    fireEvent.click(screen.getByRole('button', { name: '트러블' }));
    // 사용감은 별도 fieldset이지만 같은 concerns 배열에 실려 간다(설계 §4.1).
    fireEvent.click(screen.getByRole('button', { name: '촉촉함' }));
    fireEvent.click(screen.getByRole('button', { name: '20s' }));
    fireEvent.click(screen.getByRole('button', { name: '가입 완료' }));

    await waitFor(() => {
      expect(capturedBody).not.toBeNull();
    });

    expect(capturedBody).toMatchObject({
      skinType: 'OILY',
      concerns: ['pore', 'trouble', 'dewy'],
      ageBand: '20s',
    });
  });

  it('중복 이메일이면 서버 에러 메시지를 보여준다', async () => {
    server.use(
      http.post('/api/v1/auth/signup', () =>
        HttpResponse.json(
          { code: 'MEMBER_EMAIL_DUPLICATED', message: '이미 가입된 이메일입니다' },
          { status: 409 },
        ),
      ),
    );

    renderSignup();
    fillAccountStep();

    await screen.findByText('피부 프로필');
    fireEvent.click(screen.getByRole('button', { name: '건너뛰기' }));

    expect(await screen.findByText('이미 가입된 이메일입니다')).toBeInTheDocument();
  });
});

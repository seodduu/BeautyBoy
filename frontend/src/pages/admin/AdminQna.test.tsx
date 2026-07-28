import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { useAuthStore } from '../../stores/authStore';
import { RequireAdmin } from '../../components/auth/RequireAdmin';
import { ToastProvider } from '../../components/ui/ToastProvider';
import { AdminQna } from './AdminQna';
import * as adminApi from '../../api/admin';
import type { AdminQnaResponse } from '../../api/admin';

function envelope<T>(data: T) {
  return { code: 'OK', message: 'success', data };
}

const PUBLIC_QUESTION: AdminQnaResponse = {
  qnaId: 1,
  goodsNo: 10,
  question: '유통기한이 어떻게 되나요?',
  isSecret: false,
  status: 'ANSWERED',
  createdAt: '2026-05-20T10:00:00Z',
};

const SECRET_QUESTION: AdminQnaResponse = {
  qnaId: 2,
  goodsNo: 20,
  question: '결제 관련 개인정보 문의입니다.',
  isSecret: true,
  status: 'WAITING',
  createdAt: '2026-05-25T15:00:00Z',
};

function pageOf(content: AdminQnaResponse[]) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    hasNext: false,
  };
}

function loginAs(role: 'USER' | 'ADMIN') {
  useAuthStore.getState().setBootstrapping(false);
  useAuthStore.getState().setAuth('token', {
    id: role === 'ADMIN' ? 99 : 1,
    email: `${role.toLowerCase()}@beautyboy.dev`,
    nickname: role === 'ADMIN' ? '관리자' : '민수',
    grade: 'BRONZE',
    role,
  });
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route
              path="/admin/qna"
              element={
                <RequireAdmin>
                  <AdminQna />
                </RequireAdmin>
              }
            />
            <Route path="/main" element={<p>뷰티보이 메인</p>} />
          </Routes>
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

afterEach(() => {
  useAuthStore.getState().clear();
  vi.restoreAllMocks();
});

describe('AdminQna — 관리자 문의 관리', () => {
  it('ADMIN이 아니면 /admin은 메인으로 돌려보낸다', async () => {
    loginAs('USER');
    server.use(http.get('/api/v1/admin/qna', () => HttpResponse.json(envelope(pageOf([PUBLIC_QUESTION])))));

    renderAt('/admin/qna');

    expect(await screen.findByText(/뷰티보이/)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '문의 관리' })).not.toBeInTheDocument();
  });

  it('goodsNo 손입력 없이 전체 문의 목록을 상품번호와 함께 보여준다', async () => {
    loginAs('ADMIN');
    server.use(
      http.get('/api/v1/admin/qna', () => HttpResponse.json(envelope(pageOf([PUBLIC_QUESTION, SECRET_QUESTION])))),
    );

    renderAt('/admin/qna');

    expect(await screen.findByText('유통기한이 어떻게 되나요?')).toBeInTheDocument();
    // 공개 목록과 달리 goodsNo가 화면에 노출된다.
    expect(screen.getByText('10')).toBeInTheDocument();
    expect(screen.getByText('20')).toBeInTheDocument();
    // 상품번호 손입력 검색 UI(공개 목록 재사용 우회)는 제거됐다.
    expect(screen.queryByLabelText(/상품번호/)).not.toBeInTheDocument();
  });

  it('비밀글도 본문이 마스킹 없이 보이고 비밀글 표시가 함께 뜬다', async () => {
    // 4-14의 실제 결함 회귀 테스트: 공개 목록 재사용 경로는 비밀글을 "비밀글입니다."로
    // 마스킹해 admin이 답변할 내용을 볼 수 없었다. admin 전용 목록은 마스킹하지 않는다.
    loginAs('ADMIN');
    server.use(http.get('/api/v1/admin/qna', () => HttpResponse.json(envelope(pageOf([SECRET_QUESTION])))));

    renderAt('/admin/qna');

    expect(await screen.findByText('결제 관련 개인정보 문의입니다.')).toBeInTheDocument();
    expect(screen.queryByText('비밀글입니다.')).not.toBeInTheDocument();
    expect(screen.getByText('비밀글')).toBeInTheDocument();
  });

  it('상태는 WAITING/ANSWERED 실제 enum 값을 한글 라벨로 매핑한다', async () => {
    loginAs('ADMIN');
    server.use(
      http.get('/api/v1/admin/qna', () => HttpResponse.json(envelope(pageOf([PUBLIC_QUESTION, SECRET_QUESTION])))),
    );

    renderAt('/admin/qna');

    // 상태 열과 액션 열(답변완료 상태는 액션 대신 완료 라벨을 보여준다) 둘 다 같은 라벨을 쓰므로
    // "답변완료"는 두 곳에 나타난다.
    expect(await screen.findAllByText('답변완료')).toHaveLength(2);
    expect(screen.getByText('답변대기')).toBeInTheDocument();
    expect(screen.queryByText('PENDING')).not.toBeInTheDocument();
  });

  it('답변을 등록하면 answerAdminQna를 부르고 목록을 다시 읽는다', async () => {
    loginAs('ADMIN');
    let answered = false;
    server.use(
      http.get('/api/v1/admin/qna', () =>
        HttpResponse.json(
          envelope(pageOf([answered ? { ...SECRET_QUESTION, status: 'ANSWERED' } : SECRET_QUESTION])),
        ),
      ),
      http.post('/api/v1/admin/qna/:qnaId/answer', () => {
        answered = true;
        return HttpResponse.json(envelope(null));
      }),
    );
    const answerSpy = vi.spyOn(adminApi, 'answerAdminQna');

    renderAt('/admin/qna');

    fireEvent.click(await screen.findByRole('button', { name: '답변' }));
    fireEvent.change(screen.getByLabelText(`문의 ${SECRET_QUESTION.qnaId} 답변`), {
      target: { value: '결제 관련 문의는 마이페이지에서 확인해주세요.' },
    });
    fireEvent.click(screen.getByRole('button', { name: '등록' }));

    await waitFor(() =>
      expect(answerSpy).toHaveBeenCalledWith(SECRET_QUESTION.qnaId, '결제 관련 문의는 마이페이지에서 확인해주세요.'),
    );
    // 상태 열 + 액션 열 둘 다 "답변완료"로 바뀐다(재조회로 목록이 갱신됨).
    await waitFor(() => expect(screen.getAllByText('답변완료')).toHaveLength(2));
  });

  it('다음을 누르면 다음 페이지를 조회하고, 마지막 페이지에서는 다음이 비활성화된다', async () => {
    // 리뷰 반영: 전사 목록으로 바뀌며 goodsNo 필터가 사라져 10건(백엔드 기본 페이지 크기) 초과가
    // 흔해졌다 — page를 안 넘기면 0페이지 뒤 문의에 영구히 도달할 수 없다.
    loginAs('ADMIN');
    const PAGE_1_ITEM: AdminQnaResponse = { ...SECRET_QUESTION, qnaId: 3, goodsNo: 30 };
    server.use(
      http.get('/api/v1/admin/qna', ({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get('page') ?? '0');
        if (page === 0) {
          return HttpResponse.json(
            envelope({ content: [PUBLIC_QUESTION], page: 0, size: 20, totalElements: 2, totalPages: 2, hasNext: true }),
          );
        }
        return HttpResponse.json(
          envelope({ content: [PAGE_1_ITEM], page: 1, size: 20, totalElements: 2, totalPages: 2, hasNext: false }),
        );
      }),
    );

    renderAt('/admin/qna');

    expect(await screen.findByText(PUBLIC_QUESTION.question)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '이전' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '다음' })).not.toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '다음' }));

    await screen.findByText(PAGE_1_ITEM.question);
    expect(screen.queryByText(PUBLIC_QUESTION.question)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '이전' })).not.toBeDisabled();
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
  });

  it('문의 목록 아래에 번호 페이저를 렌더한다 — 이전/다음만으로는 오래된 문의에 닿지 못한다', async () => {
    loginAs('ADMIN');
    server.use(
      http.get('/api/v1/admin/qna', () =>
        HttpResponse.json(
          envelope({ content: [PUBLIC_QUESTION], page: 0, size: 20, totalElements: 41, totalPages: 3, hasNext: true }),
        ),
      ),
    );

    renderAt('/admin/qna');

    const pager = await screen.findByRole('navigation', { name: '페이지 이동' });
    expect(within(pager).getByRole('button', { name: '1' })).toHaveAttribute('aria-current', 'page');
    expect(within(pager).getByRole('button', { name: '3' })).toBeInTheDocument();
  });

  it('번호를 누르면 0-based page로 변환해 그 페이지를 조회한다', async () => {
    loginAs('ADMIN');
    let capturedSearchParams: URLSearchParams | undefined;
    server.use(
      http.get('/api/v1/admin/qna', ({ request }) => {
        capturedSearchParams = new URL(request.url).searchParams;
        return HttpResponse.json(
          envelope({ content: [PUBLIC_QUESTION], page: 0, size: 20, totalElements: 41, totalPages: 3, hasNext: true }),
        );
      }),
    );

    renderAt('/admin/qna');

    const pager = await screen.findByRole('navigation', { name: '페이지 이동' });
    fireEvent.click(within(pager).getByRole('button', { name: '2' }));

    await waitFor(() => {
      expect(capturedSearchParams?.get('page')).toBe('1');
    });
  });
});

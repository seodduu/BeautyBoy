import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { ToastProvider } from '../../components/ui/ToastProvider';
import { MyReviews } from './MyReviews';
import * as reviewApi from '../../api/review';
import type { MyReviewItem } from '../../api/review';

function envelope<T>(data: T) {
  return { code: 'OK', message: 'success', data };
}

const REVIEW_1: MyReviewItem = {
  reviewId: 11,
  goodsNo: 1,
  goodsName: '그린티 토너',
  thumbnailUrl: 'data:image/svg+xml;utf8,test',
  rating: 4,
  content: '원래 본문이에요',
  helpfulCount: 2,
  createdAt: '2026-07-20T10:00:00',
};

function registerHandlers(options: { updateStatus?: number; deleteStatus?: number } = {}) {
  const { updateStatus = 200, deleteStatus = 200 } = options;
  server.use(
    http.get('/api/v1/reviews/me', () =>
      HttpResponse.json(
        envelope({
          content: [REVIEW_1],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
          hasNext: false,
        }),
      ),
    ),
    http.put('/api/v1/reviews/11', () => {
      if (updateStatus >= 400) {
        return new HttpResponse(null, { status: updateStatus });
      }
      return HttpResponse.json(envelope(null));
    }),
    http.delete('/api/v1/reviews/11', () => {
      if (deleteStatus >= 400) {
        return new HttpResponse(null, { status: deleteStatus });
      }
      return HttpResponse.json(envelope(null));
    }),
  );
}

function renderMyReviews() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/mypage/reviews']}>
          <MyReviews />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
  return queryClient;
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

describe('MyReviews — 마이페이지 내 리뷰', () => {
  it('내_리뷰가_목록으로_보인다', async () => {
    registerHandlers();
    renderMyReviews();

    expect(await screen.findByText('그린티 토너')).toBeInTheDocument();
    expect(screen.getByText('원래 본문이에요')).toBeInTheDocument();
    expect(screen.getByText('4.0')).toBeInTheDocument();
  });

  it('수정을_누르면_인라인_편집이_열리고_기존_값이_채워져_있다', async () => {
    registerHandlers();
    renderMyReviews();

    await screen.findByText('그린티 토너');
    fireEvent.click(screen.getByRole('button', { name: '수정' }));

    const textarea = await screen.findByLabelText('리뷰 내용');
    expect(textarea).toHaveValue('원래 본문이에요');
  });

  it('수정을_저장하면_PUT이_나가고_토스트가_뜬다', async () => {
    registerHandlers();
    const updateSpy = vi.spyOn(reviewApi, 'updateReview');
    renderMyReviews();

    await screen.findByText('그린티 토너');
    fireEvent.click(screen.getByRole('button', { name: '수정' }));

    const textarea = await screen.findByLabelText('리뷰 내용');
    fireEvent.change(textarea, { target: { value: '고친 본문이에요' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() =>
      expect(updateSpy).toHaveBeenCalledWith(11, { rating: 4, content: '고친 본문이에요' }),
    );
    expect(await screen.findByText('수정했어요')).toBeInTheDocument();

    updateSpy.mockRestore();
  });

  it('수정_취소를_누르면_편집이_닫히고_원래_본문이_남는다', async () => {
    registerHandlers();
    const updateSpy = vi.spyOn(reviewApi, 'updateReview');
    const deleteSpy = vi.spyOn(reviewApi, 'deleteReview');
    renderMyReviews();

    await screen.findByText('그린티 토너');
    fireEvent.click(screen.getByRole('button', { name: '수정' }));

    const textarea = await screen.findByLabelText('리뷰 내용');
    fireEvent.change(textarea, { target: { value: '지워질 수정' } });
    fireEvent.click(screen.getByRole('button', { name: '취소' }));

    expect(screen.queryByLabelText('리뷰 내용')).not.toBeInTheDocument();
    expect(screen.getByText('원래 본문이에요')).toBeInTheDocument();
    expect(updateSpy).not.toHaveBeenCalled();
    expect(deleteSpy).not.toHaveBeenCalled();

    updateSpy.mockRestore();
    deleteSpy.mockRestore();
  });

  it('삭제는_확인을_거쳐야_실행된다', async () => {
    registerHandlers();
    const deleteSpy = vi.spyOn(reviewApi, 'deleteReview');
    renderMyReviews();

    await screen.findByText('그린티 토너');
    fireEvent.click(screen.getByRole('button', { name: '삭제' }));

    expect(deleteSpy).not.toHaveBeenCalled();
    expect(await screen.findByText('정말 삭제할까요?')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '삭제' }));

    await waitFor(() => expect(deleteSpy).toHaveBeenCalledWith(11));

    deleteSpy.mockRestore();
  });

  it('삭제_확인을_취소하면_호출되지_않는다', async () => {
    registerHandlers();
    const deleteSpy = vi.spyOn(reviewApi, 'deleteReview');
    renderMyReviews();

    await screen.findByText('그린티 토너');
    fireEvent.click(screen.getByRole('button', { name: '삭제' }));

    await screen.findByText('정말 삭제할까요?');
    fireEvent.click(screen.getByRole('button', { name: '취소' }));

    expect(screen.queryByText('정말 삭제할까요?')).not.toBeInTheDocument();
    expect(deleteSpy).not.toHaveBeenCalled();

    deleteSpy.mockRestore();
  });

  it('삭제에_실패하면_danger_토스트가_뜬다', async () => {
    registerHandlers({ deleteStatus: 500 });
    renderMyReviews();

    await screen.findByText('그린티 토너');
    fireEvent.click(screen.getByRole('button', { name: '삭제' }));
    await screen.findByText('정말 삭제할까요?');
    fireEvent.click(screen.getByRole('button', { name: '삭제' }));

    expect(await screen.findByText('삭제하지 못했어요. 다시 시도해 주세요')).toBeInTheDocument();
  });
});

import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AxiosError, AxiosHeaders } from 'axios';
import { useAuthStore } from '../../stores/authStore';
import { ReviewForm } from './ReviewForm';
import * as reviewApi from '../../api/review';

function renderReviewForm(queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <ReviewForm goodsNo={1} />
        </MemoryRouter>
      </QueryClientProvider>,
    ),
  };
}

function loginAsMember() {
  useAuthStore.getState().setBootstrapping(false);
  useAuthStore.getState().setAuth('token', {
    id: 1,
    email: 'user@beautyboy.dev',
    nickname: '민수',
    grade: 'BRONZE',
    role: 'USER',
  });
}

afterEach(() => {
  useAuthStore.getState().clear();
  vi.restoreAllMocks();
});

describe('ReviewForm — 리뷰 작성', () => {
  it('비로그인이면 리뷰 작성 폼 대신 로그인 안내를 보여준다', async () => {
    useAuthStore.getState().setBootstrapping(false);
    useAuthStore.getState().clear();

    renderReviewForm();

    expect(screen.queryByRole('button', { name: '등록' })).not.toBeInTheDocument();
    const loginLink = screen.getByRole('link', { name: /로그인/ });
    expect(loginLink).toBeInTheDocument();
    expect(loginLink).toHaveAttribute('href', '/login');
  });

  it('별점을 고르지 않으면 제출되지 않고 에러가 role=alert로 뜬다', async () => {
    loginAsMember();
    const createReviewSpy = vi.spyOn(reviewApi, 'createReview');

    renderReviewForm();

    fireEvent.click(screen.getByRole('button', { name: '등록' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/별점/);
    expect(createReviewSpy).not.toHaveBeenCalled();
  });

  it('구매하지 않은 상품이면 서버 메시지를 그대로 보여준다', async () => {
    loginAsMember();
    const headers = new AxiosHeaders();
    vi.spyOn(reviewApi, 'createReview').mockRejectedValue(
      new AxiosError('Forbidden', 'ERR_BAD_REQUEST', undefined, undefined, {
        status: 403,
        statusText: 'Forbidden',
        headers,
        config: { headers },
        data: { code: 'REVIEW_NOT_PURCHASED', message: '구매한 상품에만 리뷰를 쓸 수 있습니다', data: null },
      }),
    );

    renderReviewForm();

    fireEvent.click(screen.getByRole('button', { name: '별점 4점' }));
    fireEvent.click(screen.getByRole('button', { name: '등록' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/구매한 상품에만/);
  });

  it('등록에 성공하면 목록을 다시 읽고 폼을 비운다', async () => {
    loginAsMember();
    vi.spyOn(reviewApi, 'createReview').mockResolvedValue(undefined);

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    renderReviewForm(queryClient);

    fireEvent.click(screen.getByRole('button', { name: '별점 5점' }));
    fireEvent.change(screen.getByLabelText('리뷰 내용'), { target: { value: '만족스러워요' } });
    fireEvent.click(screen.getByRole('button', { name: '등록' }));

    await waitFor(() => expect(reviewApi.createReview).toHaveBeenCalledWith({
      goodsNo: 1,
      rating: 5,
      content: '만족스러워요',
    }));

    // ReviewList가 실제로 쓰는 queryKey(['reviews', goodsNo])를 무효화해야 재조회가 실제로 트리거된다.
    // no-op(엉뚱한 키 무효화)이면 이 단언이 실패한다.
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['reviews', 1] })),
    );
    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['review-stats', 1] }));

    await waitFor(() => expect(screen.getByLabelText('리뷰 내용')).toHaveValue(''));
    expect(screen.getByRole('button', { name: '별점 5점' })).toHaveAttribute('aria-pressed', 'false');
  });
});

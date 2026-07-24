import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { ToastProvider } from '../components/ui/ToastProvider';
import { Detail } from './Detail';
import type { GoodsDetail, GoodsIngredientResponse } from '../types/detail';
import type { ApiEnvelope, PageResponse } from '../types/goods';
import type { ReviewItem, ReviewStats, QnaItem } from '../types/review';

const GOODS_DETAIL: GoodsDetail = {
  goodsNo: 1,
  brandName: '어반메일',
  brandId: 1,
  name: '테스트 세럼',
  summary: '진정 효과가 좋은 세럼입니다.',
  categoryCode: 'C001002',
  categoryPath: ['스킨케어', '에센스/세럼'],
  thumbnailUrl: '',
  listPrice: 30000,
  salePrice: 24000,
  discountRate: 20,
  badges: ['SALE'],
  status: 'ON_SALE',
  options: [{ optionNo: 11, name: '기본', addPrice: 0, stock: 10, soldOut: false }],
  rating: 4.5,
  reviewCount: 12,
  wished: false,
  todayDreamAvailable: true,
};

function envelope<T>(data: T): ApiEnvelope<T> {
  return { code: 'OK', message: 'success', data };
}

function pageOf<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, hasNext: false };
}

const INGREDIENTS: GoodsIngredientResponse = {
  goodsNo: 1,
  ingredients: [],
  maxIrritation: 0,
  maxComedogenic: 0,
};

const REVIEW_STATS: ReviewStats = { reviewCount: 1, averageRating: 4.5 };
const REVIEWS: ReviewItem[] = [
  {
    reviewId: 1,
    memberId: 1,
    rating: 5,
    content: '보습력이 좋아요',
    skinType: '건성',
    helpfulCount: 3,
    createdAt: '2025-01-15T00:00:00Z',
  },
];
const QNA_ITEMS: QnaItem[] = [
  { qnaId: 1, question: '유통기한이 어떻게 되나요?', isSecret: false, status: 'ANSWERED', createdAt: '2025-02-01T00:00:00Z' },
];

function registerDefaultHandlers() {
  server.use(
    http.get('/api/v1/goods/:goodsNo', () => HttpResponse.json(envelope(GOODS_DETAIL))),
    http.get('/api/v1/goods/:goodsNo/ingredients', () => HttpResponse.json(envelope(INGREDIENTS))),
    http.get('/api/v1/reviews/stats', () => HttpResponse.json(envelope(REVIEW_STATS))),
    http.get('/api/v1/reviews', () => HttpResponse.json(envelope(pageOf(REVIEWS)))),
    http.get('/api/v1/qna', () => HttpResponse.json(envelope(pageOf(QNA_ITEMS)))),
  );
}

function renderDetail() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/goods/1']}>
          <Routes>
            <Route path="/goods/:goodsNo" element={<Detail />} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

// ToastProvider가 prefers-reduced-motion 판정에 matchMedia를 쓰므로 jsdom에 최소 구현을 채운다.
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

describe('Detail — 상세 페이지', () => {
  it('기본 정보를 렌더한다', async () => {
    registerDefaultHandlers();
    server.use(http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 201 })));

    renderDetail();

    expect(await screen.findByRole('heading', { name: '테스트 세럼' })).toBeInTheDocument();
    expect(screen.getByText('어반메일')).toBeInTheDocument();
  });

  it('장바구니 담기 클릭 시 POST /cart/items가 첫 옵션으로 나가고 성공 토스트가 뜬다', async () => {
    registerDefaultHandlers();
    let received: unknown = null;
    server.use(
      http.post('/api/v1/cart/items', async ({ request }) => {
        received = await request.json();
        return new HttpResponse(null, { status: 201 });
      }),
    );

    renderDetail();

    const button = await screen.findByRole('button', { name: '장바구니 담기' });
    fireEvent.click(button);

    await waitFor(() => expect(received).toEqual({ goodsNo: 1, optionNo: 11, quantity: 1 }));
    expect(await screen.findByText('장바구니에 담았어요')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('장바구니에 담았어요');
  });

  it('옵션이 없는 상품도 장바구니 담기가 optionNo: null로 나가고 성공 토스트가 뜬다(크래시 없음)', async () => {
    const detailWithoutOptions: GoodsDetail = { ...GOODS_DETAIL, options: [] };
    server.use(
      http.get('/api/v1/goods/:goodsNo', () => HttpResponse.json(envelope(detailWithoutOptions))),
      http.get('/api/v1/goods/:goodsNo/ingredients', () => HttpResponse.json(envelope(INGREDIENTS))),
      http.get('/api/v1/reviews/stats', () => HttpResponse.json(envelope(REVIEW_STATS))),
      http.get('/api/v1/reviews', () => HttpResponse.json(envelope(pageOf(REVIEWS)))),
      http.get('/api/v1/qna', () => HttpResponse.json(envelope(pageOf(QNA_ITEMS)))),
    );
    let received: unknown = null;
    server.use(
      http.post('/api/v1/cart/items', async ({ request }) => {
        received = await request.json();
        return new HttpResponse(null, { status: 201 });
      }),
    );

    renderDetail();

    const button = await screen.findByRole('button', { name: '장바구니 담기' });
    fireEvent.click(button);

    await waitFor(() => expect(received).toEqual({ goodsNo: 1, optionNo: null, quantity: 1 }));
    expect(await screen.findByText('장바구니에 담았어요')).toBeInTheDocument();
  });

  it('설명 탭에 상품 summary 텍스트가 보인다', async () => {
    registerDefaultHandlers();
    server.use(http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 201 })));

    renderDetail();

    const summaryTab = await screen.findByRole('tab', { name: '설명' });
    fireEvent.click(summaryTab);

    expect(await screen.findByText(GOODS_DETAIL.summary)).toBeInTheDocument();
  });

  it('장바구니 담기 실패 시 danger 톤 토스트가 뜬다', async () => {
    registerDefaultHandlers();
    server.use(http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 500 })));

    renderDetail();

    const button = await screen.findByRole('button', { name: '장바구니 담기' });
    fireEvent.click(button);

    expect(await screen.findByText('담기에 실패했어요. 다시 시도해 주세요')).toBeInTheDocument();
  });

  it('리뷰 탭을 클릭하면 리뷰 목록을 보여준다', async () => {
    registerDefaultHandlers();
    server.use(http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 201 })));

    renderDetail();

    const reviewTab = await screen.findByRole('tab', { name: '리뷰' });
    fireEvent.click(reviewTab);

    expect(await screen.findByText('보습력이 좋아요')).toBeInTheDocument();
  });

  it('리뷰가 0건이면 빈 상태를 보여준다', async () => {
    server.use(
      http.get('/api/v1/goods/:goodsNo', () => HttpResponse.json(envelope(GOODS_DETAIL))),
      http.get('/api/v1/goods/:goodsNo/ingredients', () => HttpResponse.json(envelope(INGREDIENTS))),
      http.get('/api/v1/reviews/stats', () =>
        HttpResponse.json(envelope({ reviewCount: 0, averageRating: 0 })),
      ),
      http.get('/api/v1/reviews', () => HttpResponse.json(envelope(pageOf([])))),
      http.get('/api/v1/qna', () => HttpResponse.json(envelope(pageOf(QNA_ITEMS)))),
      http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 201 })),
    );

    renderDetail();

    const reviewTab = await screen.findByRole('tab', { name: '리뷰' });
    fireEvent.click(reviewTab);

    expect(await screen.findByText('아직 등록된 리뷰가 없어요')).toBeInTheDocument();
  });

  it('Q&A 탭을 클릭하면 문의 목록을 보여준다', async () => {
    registerDefaultHandlers();
    server.use(http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 201 })));

    renderDetail();

    const qnaTab = await screen.findByRole('tab', { name: 'Q&A' });
    fireEvent.click(qnaTab);

    expect(await screen.findByText('유통기한이 어떻게 되나요?')).toBeInTheDocument();
  });

  it('Q&A가 0건이면 빈 상태를 보여준다', async () => {
    server.use(
      http.get('/api/v1/goods/:goodsNo', () => HttpResponse.json(envelope(GOODS_DETAIL))),
      http.get('/api/v1/goods/:goodsNo/ingredients', () => HttpResponse.json(envelope(INGREDIENTS))),
      http.get('/api/v1/reviews/stats', () => HttpResponse.json(envelope(REVIEW_STATS))),
      http.get('/api/v1/reviews', () => HttpResponse.json(envelope(pageOf(REVIEWS)))),
      http.get('/api/v1/qna', () => HttpResponse.json(envelope(pageOf([])))),
      http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 201 })),
    );

    renderDetail();

    const qnaTab = await screen.findByRole('tab', { name: 'Q&A' });
    fireEvent.click(qnaTab);

    expect(await screen.findByText('아직 등록된 문의가 없어요')).toBeInTheDocument();
  });

  it('화살표 키로 탭 사이를 이동할 수 있다(roving tabindex)', async () => {
    registerDefaultHandlers();
    server.use(http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 201 })));

    renderDetail();

    const summaryTab = await screen.findByRole('tab', { name: '설명' });
    const reviewTab = screen.getByRole('tab', { name: '리뷰' });

    summaryTab.focus();
    fireEvent.keyDown(summaryTab, { key: 'ArrowRight' });

    await waitFor(() => expect(reviewTab).toHaveAttribute('aria-selected', 'true'));
  });
});

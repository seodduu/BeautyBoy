import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { ToastProvider } from '../components/ui/ToastProvider';
import { Detail } from './Detail';
import type { GoodsDescription, GoodsDetail, GoodsIngredientResponse } from '../types/detail';
import type { ApiEnvelope, GoodsListItem, PageResponse } from '../types/goods';
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
  tags: [
    { name: '진정', kind: 'EFFECT', slug: 'soothing' },
    { name: '보습', kind: 'EFFECT', slug: 'moisture' },
    { name: '산뜻함', kind: 'TEXTURE', slug: 'fresh' },
  ],
};

function envelope<T>(data: T): ApiEnvelope<T> {
  return { code: 'OK', message: 'success', data };
}

function pageOf<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, hasNext: false };
}

const DESCRIPTION: GoodsDescription = {
  goodsNo: 1,
  description: '얇게 발려 겉돌지 않고 아침저녁 모두 쓸 수 있는 본문 설명입니다.',
};

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

const RECOMMENDED_ITEM: GoodsListItem = {
  goodsNo: 2,
  brandName: '포맨랩',
  name: '추천 토너',
  thumbnailUrl: '',
  listPrice: 18000,
  salePrice: 15000,
  discountRate: 17,
  badges: [],
  rating: 4.2,
  reviewCount: 5,
  wished: false,
  todayDreamAvailable: false,
  tags: [],
};

function registerDefaultHandlers(overrides: { detail?: GoodsDetail; recommended?: GoodsListItem[] } = {}) {
  server.use(
    http.get('/api/v1/goods/:goodsNo', () =>
      HttpResponse.json(envelope(overrides.detail ?? GOODS_DETAIL)),
    ),
    http.get('/api/v1/goods/:goodsNo/ingredients', () => HttpResponse.json(envelope(INGREDIENTS))),
    http.get('/api/v1/goods/:goodsNo/description', () => HttpResponse.json(envelope(DESCRIPTION))),
    http.get('/api/v1/goods/:goodsNo/recommended', () =>
      HttpResponse.json(envelope(overrides.recommended ?? [RECOMMENDED_ITEM])),
    ),
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
    // QuantityStepper의 수량 값도 role="status"라 여러 개가 매칭될 수 있어 토스트 리전으로 좁힌다.
    const toastMessage = await screen.findByText('장바구니에 담았어요');
    expect(toastMessage.closest('[role="status"]')).toHaveTextContent('장바구니에 담았어요');
  });

  it('장바구니 담기 성공 시 cart 쿼리를 무효화해 헤더 배지가 갱신되게 한다', async () => {
    registerDefaultHandlers();
    server.use(http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 201 })));
    const invalidateSpy = vi.spyOn(QueryClient.prototype, 'invalidateQueries');

    renderDetail();

    const button = await screen.findByRole('button', { name: '장바구니 담기' });
    fireEvent.click(button);

    await screen.findByText('장바구니에 담았어요');
    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['cart'] }));

    invalidateSpy.mockRestore();
  });

  it('옵션이 없는 상품도 장바구니 담기가 optionNo: null로 나가고 성공 토스트가 뜬다(크래시 없음)', async () => {
    const detailWithoutOptions: GoodsDetail = { ...GOODS_DETAIL, options: [] };
    server.use(
      http.get('/api/v1/goods/:goodsNo', () => HttpResponse.json(envelope(detailWithoutOptions))),
      http.get('/api/v1/goods/:goodsNo/ingredients', () => HttpResponse.json(envelope(INGREDIENTS))),
      http.get('/api/v1/goods/:goodsNo/description', () => HttpResponse.json(envelope(DESCRIPTION))),
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

  it('한 줄 평(summary)이 상품명 아래·가격 위에 보인다', async () => {
    registerDefaultHandlers();
    server.use(http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 201 })));

    const { container } = renderDetail();

    const summary = await screen.findByText(GOODS_DETAIL.summary);
    const heading = screen.getByRole('heading', { name: '테스트 세럼' });
    const price = container.querySelector('.bb-price');

    // compareDocumentPosition: FOLLOWING(4)이면 인자가 기준 노드보다 문서상 뒤에 있다.
    expect(heading.compareDocumentPosition(summary) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(price).not.toBeNull();
    expect(summary.compareDocumentPosition(price!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('태그 pill 줄이 한 줄 평 아래·별점 위에 최대 4개(효과 우선) 보인다', async () => {
    registerDefaultHandlers({
      detail: {
        ...GOODS_DETAIL,
        tags: [
          { name: '산뜻함', kind: 'TEXTURE', slug: 'fresh' },
          { name: '가벼운제형', kind: 'TEXTURE', slug: 'light' },
          { name: '진정', kind: 'EFFECT', slug: 'soothing' },
          { name: '보습', kind: 'EFFECT', slug: 'moisture' },
          { name: '세정', kind: 'EFFECT', slug: 'cleanse' },
        ],
      },
    });
    server.use(http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 201 })));

    const { container } = renderDetail();

    const summary = await screen.findByText(GOODS_DETAIL.summary);
    const tags = container.querySelectorAll('.bb-tag');

    expect(tags.length).toBe(4);
    // 효과(EFFECT)가 먼저 나온다.
    expect(tags[0]).toHaveTextContent('진정');
    expect(tags[1]).toHaveTextContent('보습');
    expect(tags[2]).toHaveTextContent('세정');
    expect(tags[3]).toHaveTextContent('산뜻함');
    expect(summary.compareDocumentPosition(tags[0]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('태그가 없는 상품은 태그 줄을 렌더하지 않고 나머지 화면은 그대로 뜬다', async () => {
    registerDefaultHandlers({ detail: { ...GOODS_DETAIL, tags: [] } });
    server.use(http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 201 })));

    const { container } = renderDetail();

    expect(await screen.findByRole('heading', { name: '테스트 세럼' })).toBeInTheDocument();
    expect(container.querySelectorAll('.bb-tag').length).toBe(0);
  });

  it('설명 탭은 summary가 아니라 /description 본문을 지연 로딩해 보여준다', async () => {
    registerDefaultHandlers();
    server.use(http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 201 })));

    renderDetail();

    const summaryTab = await screen.findByRole('tab', { name: '설명' });
    fireEvent.click(summaryTab);

    expect(await screen.findByText(DESCRIPTION.description)).toBeInTheDocument();
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
      http.get('/api/v1/goods/:goodsNo/description', () => HttpResponse.json(envelope(DESCRIPTION))),
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
      http.get('/api/v1/goods/:goodsNo/description', () => HttpResponse.json(envelope(DESCRIPTION))),
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

describe('Detail — 옵션 선택', () => {
  const MULTI_OPTION_DETAIL: GoodsDetail = {
    ...GOODS_DETAIL,
    salePrice: 20000,
    options: [
      { optionNo: 1, name: '200ml', addPrice: 0, stock: 50, soldOut: false },
      { optionNo: 2, name: '300ml', addPrice: 3000, stock: 80, soldOut: false },
    ],
  };

  const SOLD_OUT_OPTION_DETAIL: GoodsDetail = {
    ...GOODS_DETAIL,
    options: [
      { optionNo: 1, name: '200ml', addPrice: 0, stock: 10, soldOut: false },
      { optionNo: 2, name: '500ml', addPrice: 5000, stock: 0, soldOut: false },
    ],
  };

  it('옵션이 2개 이상이면 선택 UI를 보여주고, 처음에는 아무것도 선택되지 않는다', async () => {
    registerDefaultHandlers({ detail: MULTI_OPTION_DETAIL });

    renderDetail();

    expect(await screen.findByRole('radiogroup', { name: /옵션/ })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /300ml/ })).not.toBeChecked();
    expect(screen.getByRole('button', { name: '장바구니 담기' })).toBeDisabled();
  });

  it('옵션을 고르면 표시 가격에 addPrice가 더해진다', async () => {
    registerDefaultHandlers({ detail: MULTI_OPTION_DETAIL });

    renderDetail();

    fireEvent.click(await screen.findByRole('radio', { name: /300ml/ }));

    expect(screen.getByTestId('detail-price')).toHaveTextContent('23,000');
  });

  it('품절 옵션은 선택할 수 없다', async () => {
    registerDefaultHandlers({ detail: SOLD_OUT_OPTION_DETAIL });

    renderDetail();

    expect(await screen.findByRole('radio', { name: /품절/ })).toBeDisabled();
  });

  it('옵션이 하나뿐이면 자동 선택하고 선택 UI를 그리지 않는다', async () => {
    registerDefaultHandlers();

    renderDetail();

    expect(await screen.findByRole('button', { name: '장바구니 담기' })).toBeEnabled();
    expect(screen.queryByRole('radiogroup')).not.toBeInTheDocument();
  });

  it('선택한 옵션과 수량으로 담는다', async () => {
    registerDefaultHandlers({ detail: MULTI_OPTION_DETAIL });
    let received: unknown = null;
    server.use(
      http.post('/api/v1/cart/items', async ({ request }) => {
        received = await request.json();
        return new HttpResponse(null, { status: 201 });
      }),
    );

    renderDetail();

    fireEvent.click(await screen.findByRole('radio', { name: /300ml/ }));
    fireEvent.click(screen.getByRole('button', { name: '수량 늘리기' }));
    fireEvent.click(screen.getByRole('button', { name: '장바구니 담기' }));

    await waitFor(() => expect(received).toEqual({ goodsNo: 1, optionNo: 2, quantity: 2 }));
  });
});

describe('Detail — 추천 상품', () => {
  it('추천 상품이 있으면 "함께 보면 좋은 상품" 섹션과 카드를 렌더한다', async () => {
    registerDefaultHandlers({ recommended: [RECOMMENDED_ITEM] });
    server.use(http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 201 })));

    renderDetail();

    expect(await screen.findByRole('heading', { name: '함께 보면 좋은 상품' })).toBeInTheDocument();
    expect(await screen.findByText('추천 토너')).toBeInTheDocument();
  });

  it('추천 상품이 없으면 섹션 자체를 렌더하지 않는다', async () => {
    registerDefaultHandlers({ recommended: [] });
    server.use(http.post('/api/v1/cart/items', () => new HttpResponse(null, { status: 201 })));

    renderDetail();

    await screen.findByRole('heading', { name: '테스트 세럼' });
    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: '함께 보면 좋은 상품' })).not.toBeInTheDocument(),
    );
  });
});

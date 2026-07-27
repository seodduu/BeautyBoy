import { beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { ToastProvider } from '../../components/ui/ToastProvider';
import { PickCard } from './PickCard';
import type { GoodsListItem } from '../../types/goods';
import type { GoodsOption } from '../../types/detail';

// ToastProvider가 prefers-reduced-motion 판정에 matchMedia를 쓰므로 jsdom에 최소 구현을 채운다.
beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    }),
  });
});

const PICK: GoodsListItem = {
  goodsNo: 501,
  brandName: '어반메일',
  name: '모공 케어 세럼',
  thumbnailUrl: '',
  listPrice: 20000,
  salePrice: 20000,
  discountRate: 0,
  badges: [],
  rating: 0,
  reviewCount: 0,
  wished: false,
  todayDreamAvailable: false,
  tags: [
    { name: '모공 케어', kind: 'EFFECT', slug: 'pore' },
    { name: '진정', kind: 'EFFECT', slug: 'soothe' },
    { name: '보습', kind: 'EFFECT', slug: 'moisture' },
  ],
};

/** 실제로 나간 POST /cart/items 바디. 어떤 옵션으로 담았는지를 여기서 본다. */
let cartPosts: { goodsNo: number; optionNo: number | null; quantity: number }[] = [];

function serveDetail(options: GoodsOption[]) {
  server.use(
    http.get('/api/v1/goods/:goodsNo', ({ params }) =>
      HttpResponse.json({
        code: 'OK',
        message: 'success',
        data: {
          goodsNo: Number(params.goodsNo),
          brandName: PICK.brandName,
          brandId: 1,
          name: PICK.name,
          summary: '',
          categoryCode: 'C001002001',
          categoryPath: [],
          thumbnailUrl: '',
          listPrice: PICK.listPrice,
          salePrice: PICK.salePrice,
          discountRate: 0,
          badges: [],
          status: 'ON_SALE',
          options,
          rating: 0,
          reviewCount: 0,
          wished: false,
          todayDreamAvailable: false,
          tags: PICK.tags,
        },
      }),
    ),
  );
}

function option(partial: Partial<GoodsOption>): GoodsOption {
  return { optionNo: 1, name: '기본', addPrice: 0, stock: 10, soldOut: false, ...partial };
}

function renderPickCard(props: Partial<React.ComponentProps<typeof PickCard>> = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>
          <PickCard
            pick={PICK}
            reason={null}
            matched={{ concerns: [], behaviors: [] }}
            {...props}
          />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  cartPosts = [];
  server.use(
    http.post('/api/v1/cart/items', async ({ request }) => {
      cartPosts.push((await request.json()) as (typeof cartPosts)[number]);
      return HttpResponse.json({ code: 'OK', message: 'success', data: null }, { status: 201 });
    }),
  );
  serveDetail([option({ optionNo: 11 })]);
});

describe('PickCard — 대표 픽 카드', () => {
  it('PickCard: reason과 근거 칩(고민 일치 태그)이 렌더된다', () => {
    renderPickCard({
      reason: '모공은 세럼 단계에서 정면으로 잡는 게 효율적이에요',
      matched: { concerns: ['pore'], behaviors: [] },
    });

    // reason 문장은 규칙 원문 그대로 — 화면이 만들지 않는다.
    expect(
      screen.getByText('모공은 세럼 단계에서 정면으로 잡는 게 효율적이에요'),
    ).toBeInTheDocument();

    // 근거 칩은 "점수에 실제 기여한 태그"만 — 상품이 가진 나머지 태그는 칩이 되지 않는다.
    const chips = screen.getByTestId('pick-card-chips');
    expect(chips).toHaveTextContent('모공 케어');
    expect(chips).not.toHaveTextContent('진정');
    expect(chips).not.toHaveTextContent('보습');
  });

  it('PickCard: 바로 담기 클릭 → 재고 첫 옵션으로 cart POST가 나가고 토스트가 뜬다', async () => {
    // 첫 옵션은 품절, 두 번째가 재고 있음 — "재고 있는 첫 옵션"이 12가 되어야 한다.
    serveDetail([
      option({ optionNo: 11, stock: 0, soldOut: true }),
      option({ optionNo: 12, stock: 3 }),
    ]);
    renderPickCard();

    fireEvent.click(await screen.findByRole('button', { name: '바로 담기' }));

    await waitFor(() =>
      expect(cartPosts).toEqual([{ goodsNo: 501, optionNo: 12, quantity: 1 }]),
    );
    expect(await screen.findByText('담았어요 — 옵션 변경은 장바구니에서')).toBeInTheDocument();
  });

  it('PickCard: 전 옵션 품절이면 버튼이 비활성이다', async () => {
    serveDetail([
      option({ optionNo: 11, stock: 0, soldOut: true }),
      option({ optionNo: 12, stock: 0, soldOut: true }),
    ]);
    renderPickCard();

    await waitFor(() => expect(screen.getByRole('button', { name: '바로 담기' })).toBeDisabled());
    // 색·비활성만으로 알리지 않는다 — 문구를 함께 낸다(DESIGN.md 품절 규칙).
    expect(screen.getByText('일시품절')).toBeInTheDocument();
    expect(cartPosts).toEqual([]);
  });
});

import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { ToastProvider } from '../components/ui/ToastProvider';
import { Sets } from './Sets';
import { ROUTINE_STEPS } from '../features/routine/steps';
import { POOL_SIZE } from '../features/affinity/composer';
import { useAuthStore } from '../stores/authStore';
import type { GoodsListItem, TagView } from '../types/goods';
import type { FlowRulesResponse } from '../types/routine';

beforeAll(() => {
  // jsdom에는 IntersectionObserver가 없다 — SetBand/GoodsCard가 lazy 렌더에 안 쓰더라도
  // 다른 하위 컴포넌트가 쓸 가능성을 Main.test.tsx와 같은 방식으로 막아둔다.
  class MockIntersectionObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
    takeRecords() {
      return [];
    }
    root = null;
    rootMargin = '';
    thresholds = [];
  }
  vi.stubGlobal('IntersectionObserver', MockIntersectionObserver);

  // ToastProvider가 prefers-reduced-motion 판정에 matchMedia를 쓴다.
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

const rulesFixture: FlowRulesResponse = {
  version: 'test-1',
  flowRules: [],
  concernRules: [],
};

const TAG_NAMES: Record<string, string> = {
  pore: '모공 케어',
  trouble: '트러블 케어',
  moisture: '보습',
};

function tagOf(slug: string): TagView {
  return { name: TAG_NAMES[slug] ?? slug, kind: 'EFFECT', slug };
}

/**
 * 단계별 후보 풀. Main.test.tsx와 같은 규약 — goodsNo는 (단계 순번+1)×100 + 인기순위라
 * 어떤 단계의 몇 위 상품인지 번호만 보고 드러난다.
 */
function poolFor(categoryCode: string): GoodsListItem[] {
  const stepIndex = ROUTINE_STEPS.findIndex((step) => step.categoryCode === categoryCode);
  if (stepIndex < 0) {
    return [];
  }
  const base = (stepIndex + 1) * 100;

  return Array.from({ length: POOL_SIZE }, (_, i) => {
    const goodsNo = base + i + 1;
    return {
      goodsNo,
      brandName: '브랜드',
      name: `${ROUTINE_STEPS[stepIndex].label} ${i + 1}위`,
      thumbnailUrl: '',
      listPrice: 10000,
      salePrice: 10000,
      discountRate: 0,
      badges: [],
      rating: 0,
      reviewCount: 0,
      wished: false,
      todayDreamAvailable: false,
      tags: (tagPlan[goodsNo] ?? []).map(tagOf),
    };
  });
}

/** goodsNo → 붙일 태그 슬러그. */
let tagPlan: Record<number, string[]> = {};
/** 전 옵션 품절로 만들 goodsNo. */
let soldOutGoodsNos: number[] = [];
/** 실제로 나간 POST /cart/items 바디. */
let cartPosts: { goodsNo: number; optionNo: number | null; quantity: number }[] = [];

function serveGoods() {
  server.use(
    http.get('/api/v1/goods', ({ request }) => {
      const url = new URL(request.url);
      const categoryCode = url.searchParams.get('categoryCode');
      const content = poolFor(categoryCode ?? '');
      return HttpResponse.json({
        code: 'OK',
        message: 'success',
        data: {
          content,
          page: 0,
          size: content.length,
          totalElements: content.length,
          totalPages: 1,
          hasNext: false,
        },
      });
    }),
  );
}

/** 합성 goodsNo의 상세. 클렌징 단계만 카테고리 코드가 대분류 4자(C002)라 중분류를 따로 받아야 한다. */
function serveGoodsDetail() {
  server.use(
    http.get('/api/v1/goods/:goodsNo', ({ params }) => {
      const goodsNo = Number(params.goodsNo);
      const stepIndex = Math.floor(goodsNo / 100) - 1;
      const step = ROUTINE_STEPS[stepIndex];
      if (!step) {
        return HttpResponse.json(
          { code: 'GOODS_NOT_FOUND', message: '상품을 찾을 수 없습니다.', data: null },
          { status: 404 },
        );
      }
      const soldOut = soldOutGoodsNos.includes(goodsNo);

      return HttpResponse.json({
        code: 'OK',
        message: 'success',
        data: {
          goodsNo,
          brandName: '브랜드',
          brandId: 1,
          name: `${step.label} 상세`,
          summary: '',
          categoryCode: step.categoryCode === 'C002' ? 'C002001001' : `${step.categoryCode}001`,
          categoryPath: [],
          thumbnailUrl: '',
          listPrice: 10000,
          salePrice: 10000,
          discountRate: 0,
          badges: [],
          status: 'ON_SALE',
          options: [
            {
              optionNo: goodsNo * 10 + 1,
              name: '기본',
              addPrice: 0,
              stock: soldOut ? 0 : 10,
              soldOut,
            },
          ],
          rating: 0,
          reviewCount: 0,
          wished: false,
          todayDreamAvailable: false,
          tags: (tagPlan[goodsNo] ?? []).map(tagOf),
        },
      });
    }),
  );
}

function serveVerdicts() {
  server.use(
    http.get('/api/v1/compat/verdicts', () =>
      HttpResponse.json({ code: 'OK', message: 'success', data: {} }),
    ),
  );
}

function serveRules(rules: FlowRulesResponse) {
  server.use(
    http.get('/api/v1/routine/flow-rules', () =>
      HttpResponse.json({ code: 'OK', message: 'success', data: rules }),
    ),
  );
}

function serveMe(profile: { skinType: string | null; concerns: string[] }) {
  useAuthStore.setState({ accessToken: 'test-token' });
  server.use(
    http.get('/api/v1/members/me', () =>
      HttpResponse.json({
        code: 'OK',
        message: 'success',
        data: {
          id: 1,
          email: 'test@beautyboy.dev',
          nickname: '민수',
          grade: 'BRONZE',
          ageBand: '20s',
          ...profile,
        },
      }),
    ),
  );
}

function renderSets() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>
          <Sets />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  tagPlan = {};
  soldOutGoodsNos = [];
  cartPosts = [];
  useAuthStore.setState({ accessToken: null });
  serveRules(rulesFixture);
  serveGoods();
  serveGoodsDetail();
  serveVerdicts();
  server.use(
    http.post('/api/v1/cart/items', async ({ request }) => {
      cartPosts.push((await request.json()) as (typeof cartPosts)[number]);
      return HttpResponse.json({ code: 'OK', message: 'success', data: null }, { status: 201 });
    }),
  );
});

afterEach(() => {
  useAuthStore.setState({ accessToken: null });
});

describe('/sets 세트 비교 페이지', () => {
  it('프로필 회원 — 밴드 3개가 렌더되고 제목이 "세트 A · <컨셉>" 형식이다', async () => {
    serveMe({ skinType: null, concerns: ['pore', 'trouble', 'moisture'] });

    renderSets();

    const bands = await screen.findAllByRole('heading', { level: 2 });
    expect(bands).toHaveLength(3);
    expect(bands[0]).toHaveTextContent('세트 A · 모공');
    expect(bands[1]).toHaveTextContent('세트 B · 트러블');
    expect(bands[2]).toHaveTextContent('세트 C · 보습');
  });

  it('각 밴드에 5단계가 순서대로 있다', async () => {
    serveMe({ skinType: null, concerns: ['pore', 'trouble', 'moisture'] });

    renderSets();

    const firstBandTitle = (await screen.findAllByRole('heading', { level: 2 }))[0];
    const band = firstBandTitle.closest('section');
    expect(band).not.toBeNull();

    const labels = within(band as HTMLElement)
      .getAllByText(/^\d{2} /)
      .map((el) => el.textContent);
    expect(labels).toEqual(
      ROUTINE_STEPS.map((step) => `${String(step.order).padStart(2, '0')} ${step.label}`),
    );
  });

  it('세트 차별화 — 세 밴드의 세럼 단계 픽이 서로 다르다', async () => {
    serveMe({ skinType: null, concerns: ['pore', 'trouble', 'moisture'] });
    // 세럼(C001002, base=300) 풀에 세 컨셉 태그를 각각 다른 상품에 심는다.
    // 인기 1위(301)는 무태그로 둬 콜드스타트 기준선과 구분한다.
    tagPlan = {
      304: ['pore'],
      305: ['trouble'],
      306: ['moisture'],
    };

    renderSets();

    const bands = await screen.findAllByRole('heading', { level: 2 });
    expect(bands).toHaveLength(3);

    const serumNames = bands.map((bandTitle) => {
      const band = bandTitle.closest('section') as HTMLElement;
      const cell = within(band).getByText('03 에센스/세럼').closest('li') as HTMLElement;
      return within(cell).getByText(/^에센스\/세럼 \d+위$/).textContent;
    });

    expect(serumNames).toEqual(['에센스/세럼 4위', '에센스/세럼 5위', '에센스/세럼 6위']);
    expect(new Set(serumNames).size).toBe(3);
  });

  it('세트별 담기 — 그 세트의 픽만 담긴다 (다른 세트 것이 섞이지 않는다)', async () => {
    serveMe({ skinType: null, concerns: ['pore', 'trouble', 'moisture'] });
    tagPlan = {
      304: ['pore'],
      305: ['trouble'],
      306: ['moisture'],
    };

    renderSets();

    const bands = await screen.findAllByRole('heading', { level: 2 });
    const bandB = bands[1].closest('section') as HTMLElement;

    // 세트 B(트러블)의 5단계 픽 goodsNo를 화면에서 그대로 읽어낸다: 클렌징/토너/크림/선크림은
    // 무태그라 인기 1위(101/201/401/501), 세럼만 트러블 태그가 붙은 305(=5위)다.
    const expectedGoodsNos = [101, 201, 305, 401, 501];

    const addButton = within(bandB).getByRole('button', { name: /이 세트 \d+개 담기/ });
    await waitFor(() => expect(addButton).toBeEnabled());
    fireEvent.click(addButton);

    await waitFor(() => expect(cartPosts.length).toBe(expectedGoodsNos.length));
    expect(cartPosts.map((post) => post.goodsNo).sort((a, b) => a - b)).toEqual(
      [...expectedGoodsNos].sort((a, b) => a - b),
    );
  });

  it('일부 품절이면 집계 문구가 나온다', async () => {
    serveMe({ skinType: null, concerns: ['pore', 'trouble', 'moisture'] });
    // 세트 A(모공)의 클렌징 픽(101)만 전 옵션 품절로 만든다.
    soldOutGoodsNos = [101];

    renderSets();

    const bands = await screen.findAllByRole('heading', { level: 2 });
    const bandA = bands[0].closest('section') as HTMLElement;
    const addButton = within(bandA).getByRole('button', { name: /이 세트 \d+개 담기/ });
    await waitFor(() => expect(addButton).toBeEnabled());
    fireEvent.click(addButton);

    expect(await screen.findByText('4개 담았어요 — 1개는 품절로 제외')).toBeInTheDocument();
  });

  it('프로필 미입력 회원 — 폴백 3종 라벨과 등록 유도 문구가 보인다', async () => {
    serveMe({ skinType: null, concerns: [] });

    renderSets();

    const bands = await screen.findAllByRole('heading', { level: 2 });
    expect(bands.map((b) => b.textContent)).toEqual([
      expect.stringContaining('모공'),
      expect.stringContaining('트러블'),
      expect.stringContaining('보습'),
    ]);
    expect(screen.getByText(/프로필을 등록하면 맞춤 세트로 바뀌어요/)).toBeInTheDocument();
  });
});

import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { delay, http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { ToastProvider } from '../components/ui/ToastProvider';
import { Main } from './Main';
import { ROUTINE_STEPS } from '../features/routine/steps';
import { POOL_SIZE } from '../features/affinity/composer';
import { useAuthStore } from '../stores/authStore';
import type { AffinityEvent } from '../features/affinity/events';
import type { GoodsListItem, TagView } from '../types/goods';
import type { FlowRulesResponse } from '../types/routine';

beforeAll(() => {
  // jsdom에는 IntersectionObserver가 없다. scroll-spy는 브라우저 동작이므로
  // 여기서는 "네비가 렌더되고 앵커가 맞는가"만 검증하고 관찰 자체는 무력화한다.
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

  // ToastProvider가 prefers-reduced-motion 판정에 matchMedia를 쓰므로 jsdom에 최소 구현을 채운다.
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
  flowRules: [
    {
      fromCategoryCode: 'C002001',
      fromTagSlug: null,
      toCategoryCode: 'C001001',
      toTagSlug: 'moisture',
      edgeKind: 'NEXT_STEP',
      reason: '세안 다음 단계는 수분 충전이에요',
      priority: 20,
    },
  ],
  concernRules: [
    {
      concernTagSlug: 'pore',
      toCategoryCode: 'C001002',
      toTagSlug: 'pore',
      reason: '모공은 세럼 단계에서 정면으로 잡는 게 효율적이에요',
      priority: 10,
    },
  ],
};

const TAG_NAMES: Record<string, string> = {
  pore: '모공 케어',
  moisture: '보습',
  soothe: '진정',
};

function tagOf(slug: string): TagView {
  return { name: TAG_NAMES[slug] ?? slug, kind: 'EFFECT', slug };
}

/**
 * 단계별 후보 풀을 테스트가 통째로 통제한다. goodsNo는 `(단계 순번+1)×100 + 인기순위`라
 * 어떤 단계의 몇 위 상품인지가 번호만 보고 드러난다(101 = 클렌징 1위).
 * 이름에도 순위를 넣어 "픽이 인기 1위인가"를 화면 텍스트로 단언할 수 있게 한다.
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

/** goodsNo → 붙일 태그 슬러그. 테스트마다 바꿔 개인화 신호가 실제로 순위를 흔드는지 본다. */
let tagPlan: Record<number, string[]> = {};
/** 전 옵션 품절로 만들 goodsNo — 전체 담기 집계를 검증할 때 쓴다. */
let soldOutGoodsNos: number[] = [];
/** 실제로 나간 POST /cart/items 바디. */
let cartPosts: { goodsNo: number; optionNo: number | null; quantity: number }[] = [];
/** 실제로 나간 GET /goods 요청의 (categoryCode, size). */
let goodsCalls: { categoryCode: string | null; size: string | null }[] = [];

function serveGoods() {
  server.use(
    http.get('/api/v1/goods', ({ request }) => {
      const url = new URL(request.url);
      const categoryCode = url.searchParams.get('categoryCode');
      goodsCalls.push({ categoryCode, size: url.searchParams.get('size') });

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

/**
 * 합성 goodsNo의 상세. 클렌징 단계만 카테고리 코드가 대분류 4자(C002)라 픽의 중분류를
 * 여기서 받아야 전이 규칙(from_category_code 7자)이 매칭된다.
 */
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
          // 클렌징(C002)의 픽은 중분류가 C002001이라야 전이 규칙이 걸린다.
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

/** base goodsNo → (후보 goodsNo → verdict). 비우면 판정이 전부 없는(=통과) 상태다. */
function serveVerdicts(table: Record<number, Record<number, string>>) {
  server.use(
    http.get('/api/v1/compat/verdicts', ({ request }) => {
      const url = new URL(request.url);
      const base = Number(url.searchParams.get('base'));
      const candidates = (url.searchParams.get('candidates') ?? '')
        .split(',')
        .filter(Boolean)
        .map(Number);

      const row = table[base] ?? {};
      const data: Record<string, string> = {};
      for (const candidate of candidates) {
        if (row[candidate]) {
          data[String(candidate)] = row[candidate];
        }
      }
      return HttpResponse.json({ code: 'OK', message: 'success', data });
    }),
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

function seedEvents(events: AffinityEvent[]) {
  localStorage.setItem('bb.affinity.v1', JSON.stringify(events));
}

function renderMain() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>
          <Main />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

/** 섹션 하나를 제목으로 집어온다 — 픽은 그 섹션 안에서만 찾아야 옆 섹션과 헷갈리지 않는다. */
function sectionOf(label: string) {
  return screen.getByRole('region', { name: label });
}

/** 그 섹션의 대표 픽 상품명. 픽 카드의 상품명은 섹션 안 유일한 h3다. */
async function pickNameOf(label: string) {
  return (await within(sectionOf(label)).findByRole('heading', { level: 3 })).textContent ?? '';
}

beforeEach(() => {
  tagPlan = {};
  soldOutGoodsNos = [];
  cartPosts = [];
  goodsCalls = [];
  localStorage.clear();
  useAuthStore.setState({ accessToken: null });
  serveRules(rulesFixture);
  serveGoods();
  serveGoodsDetail();
  serveVerdicts({});
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

describe('Main — 루틴 메인 페이지', () => {
  it('루틴 5단계를 상수 순서대로 렌더한다', () => {
    renderMain();

    const headings = screen.getAllByRole('heading', { level: 2 });
    expect(headings.map((h) => h.textContent)).toEqual([
      '클렌징',
      '토너/스킨',
      '에센스/세럼',
      '로션/크림',
      '선크림',
    ]);
  });

  it('앵커 네비가 5단계를 모두 가리킨다', () => {
    renderMain();

    const nav = screen.getByRole('navigation', { name: '루틴 단계 바로가기' });
    for (const step of ROUTINE_STEPS) {
      expect(within(nav).getByRole('link', { name: new RegExp(step.label) })).toHaveAttribute(
        'href',
        `#${step.id}`,
      );
    }
  });

  it('인트로 제목이 페이지의 유일한 h1이다', () => {
    renderMain();

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('단계마다 조합기 풀(POOL_SIZE)을 한 번씩 받아 온다', async () => {
    renderMain();

    await waitFor(() => expect(goodsCalls).toHaveLength(ROUTINE_STEPS.length));
    expect(goodsCalls.every((call) => call.size === String(POOL_SIZE))).toBe(true);
  });
});

describe('Main — 루틴 조합기', () => {
  it('Main: 콜드스타트면 5단계 픽이 전부 인기 1위이고 reason이 없다', async () => {
    // 전이 규칙은 사용자 신호가 아니라 "앞 픽"에서 나온다 — 콜드스타트에서도 발동한다(설계 §3.2).
    // 이 테스트의 주제는 "신호가 전무하면 구성이 기준선인가"이므로 전이 규칙 축은 비우고,
    // 그 축은 아래 '이전 픽이 전이 규칙을 발동하면 …' 테스트가 따로 덮는다.
    serveRules({ ...rulesFixture, flowRules: [] });

    renderMain();

    for (const step of ROUTINE_STEPS) {
      expect(await pickNameOf(step.label)).toBe(`${step.label} 1위`);
    }

    // 신호가 없으면 발동할 규칙도 없다 — 문장 없이 카드만 남는다(설계 §3.2 폴백 사다리).
    expect(screen.queryByText('세안 다음 단계는 수분 충전이에요')).not.toBeInTheDocument();
    expect(
      screen.queryByText('모공은 세럼 단계에서 정면으로 잡는 게 효율적이에요'),
    ).not.toBeInTheDocument();
  });

  it('이전 픽이 전이 규칙을 발동하면 다음 단계에 reason과 flow 보너스가 붙는다', async () => {
    // 토너 5위만 규칙의 to_tag(moisture)를 갖는다 — flow 1.0이 인기 0.3을 이겨 픽이 5위로 바뀐다.
    tagPlan = { 205: ['moisture'] };

    renderMain();

    expect(await pickNameOf('토너/스킨')).toBe('토너/스킨 5위');
    expect(
      within(sectionOf('토너/스킨')).getByText('세안 다음 단계는 수분 충전이에요'),
    ).toBeInTheDocument();
  });

  it('Main: 고민 프로필이 있으면 픽이 인기 1위와 달라지는 단계가 존재하고 reason이 렌더된다', async () => {
    serveMe({ skinType: 'COMBINATION', concerns: ['pore'] });
    // 세럼 4위 상품만 고민(pore) 태그를 갖는다 — 고민 2.0이 인기 0.3을 이겨 픽이 4위로 바뀐다.
    tagPlan = { 304: ['pore'] };

    renderMain();

    expect(await pickNameOf('에센스/세럼')).toBe('에센스/세럼 4위');
    // 나머지 단계는 신호가 안 걸려 기준선 그대로다 — 픽이 전부 바뀌면 개인화가 아니라 다른 화면이다.
    expect(await pickNameOf('로션/크림')).toBe('로션/크림 1위');

    const serum = sectionOf('에센스/세럼');
    expect(
      within(serum).getByText('모공은 세럼 단계에서 정면으로 잡는 게 효율적이에요'),
    ).toBeInTheDocument();
  });

  it('Main: 이전 픽과 CONFLICT인 후보는 다음 단계 픽·대안에 나오지 않는다', async () => {
    // 클렌징 픽(101)과 토너 1·2위(201·202)가 충돌한다 → 토너 픽은 3위로 밀리고
    // 대안(4~6위)에도 충돌 후보가 끼지 않는다.
    serveVerdicts({ 101: { 201: 'CONFLICT', 202: 'CONFLICT' } });

    renderMain();

    expect(await pickNameOf('토너/스킨')).toBe('토너/스킨 3위');

    const toner = sectionOf('토너/스킨');
    expect(within(toner).queryByText('토너/스킨 1위')).not.toBeInTheDocument();
    expect(within(toner).queryByText('토너/스킨 2위')).not.toBeInTheDocument();
    expect(within(toner).getByText('토너/스킨 4위')).toBeInTheDocument();
  });

  it('Main: verdicts 요청이 실패해도 체인이 멈추지 않는다', async () => {
    server.use(http.get('/api/v1/compat/verdicts', () => new HttpResponse(null, { status: 500 })));

    renderMain();

    // 게이트만 생략되고 5단계가 전부 확정된다(설계 §3.3 — 궁합 확인 실패로 메인이 멈추면 안 된다).
    await waitFor(() =>
      expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(ROUTINE_STEPS.length),
    );
    expect(await pickNameOf('토너/스킨')).toBe('토너/스킨 1위');
  });

  it('Main: 위 단계가 미확정이면 아래 섹션은 스켈레톤이다', async () => {
    // 클렌징 풀만 영원히 응답하지 않는다 — 체인이 1단계에서 멈춘다.
    server.use(
      http.get('/api/v1/goods', async ({ request }) => {
        const url = new URL(request.url);
        const categoryCode = url.searchParams.get('categoryCode');
        goodsCalls.push({ categoryCode, size: url.searchParams.get('size') });
        if (categoryCode === ROUTINE_STEPS[0].categoryCode) {
          await delay('infinite');
        }
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

    const { container } = renderMain();

    // 아래 네 단계의 풀은 전부 도착한다 — 여기서 멈추는 이유는 데이터가 아니라 체인이다.
    await waitFor(() => expect(goodsCalls).toHaveLength(ROUTINE_STEPS.length));
    // 도착한 응답이 렌더에 반영될 시간을 준다. 이 여유가 없으면 "아직 안 그려졌을 뿐"인 상태를
    // 스켈레톤으로 오독해 체인이 깨져도 통과한다(점진 렌더를 무력화해도 초록인 테스트가 된다).
    await new Promise((resolve) => setTimeout(resolve, 100));

    // 자기 풀을 이미 받았어도 앞 픽이 앵커라 확정될 수 없다 — 스켈레톤을 유지한다.
    expect(container.querySelectorAll('.bb-goods-card-skeleton').length).toBeGreaterThan(0);
    expect(screen.queryAllByRole('heading', { level: 3 })).toHaveLength(0);
  });

  it('전체 담기: 픽 5개 중 1개 실패 시 4개 담고 집계 토스트를 띄운다', async () => {
    // 로션/크림 픽(401)만 전 옵션 품절 — 실패는 건너뛰고 나머지는 담는다(설계 §4.3).
    soldOutGoodsNos = [401];

    renderMain();

    const cta = await screen.findByRole('button', { name: '오늘의 루틴 5개 담기' });
    await waitFor(() => expect(cta).toBeEnabled());
    fireEvent.click(cta);

    expect(await screen.findByText('4개 담았어요 — 1개는 품절로 제외')).toBeInTheDocument();
    expect(cartPosts.map((post) => post.goodsNo)).toEqual([101, 201, 301, 501]);
  });

  it('전체 담기: 전부 성공하면 담은 개수만 알린다', async () => {
    renderMain();

    const cta = await screen.findByRole('button', { name: '오늘의 루틴 5개 담기' });
    await waitFor(() => expect(cta).toBeEnabled());
    fireEvent.click(cta);

    expect(await screen.findByText('5개 담았어요')).toBeInTheDocument();
    expect(cartPosts).toHaveLength(5);
  });

  it('flow-rules 요청이 실패해도 조합은 계속된다 — reason만 사라진다', async () => {
    serveMe({ skinType: 'COMBINATION', concerns: ['pore'] });
    tagPlan = { 304: ['pore'] };
    server.use(
      http.get('/api/v1/routine/flow-rules', () => new HttpResponse(null, { status: 500 })),
    );

    renderMain();

    // 규칙이 없어도 점수 공식은 그대로 돈다 — 고민 태그가 붙은 4위가 여전히 픽이다.
    expect(await pickNameOf('에센스/세럼')).toBe('에센스/세럼 4위');
    expect(
      screen.queryByText('모공은 세럼 단계에서 정면으로 잡는 게 효율적이에요'),
    ).not.toBeInTheDocument();
  });

  it('행동 신호가 쌓이면 그 태그를 가진 후보가 픽으로 올라온다', async () => {
    // 세럼(C001002)에서 진정 태그 상품을 반복해서 봤다 — 행동 1.5가 인기 0.3을 이긴다.
    seedEvents(
      Array.from({ length: 5 }, (_, i) => ({
        goodsNo: 300 + i,
        cat3: 'C001002',
        tags: ['soothe'],
        w: 1 as const,
      })),
    );
    tagPlan = { 306: ['soothe'] };

    renderMain();

    expect(await pickNameOf('에센스/세럼')).toBe('에센스/세럼 6위');
  });
});

describe('세트 진입 링크 (/sets 스펙 §3)', () => {
  it('세트 탭이 더 이상 렌더되지 않는다', async () => {
    serveMe({ skinType: null, concerns: ['pore', 'trouble', 'moisture'] });

    renderMain();

    // 화면이 뜬 뒤 판정. 텍스트 '클렌징'은 앵커 네비 라벨과 섹션 제목 양쪽에 있어 모호하므로
    // 섹션 제목(h2)으로 단일 매치를 잡는다.
    await screen.findByRole('heading', { level: 2, name: '클렌징' });
    expect(screen.queryAllByRole('tab')).toHaveLength(0);
    expect(screen.queryByText(/당신을 위한 세트/)).not.toBeInTheDocument();
  });

  it('히어로에 /sets로 가는 링크가 있다', async () => {
    renderMain();

    const link = await screen.findByRole('link', { name: /세트 보러가기/ });
    expect(link).toHaveAttribute('href', '/sets');
  });
});

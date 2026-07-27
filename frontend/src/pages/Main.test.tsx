import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { Main } from './Main';
import { ROUTINE_STEPS } from '../features/routine/steps';
import { useAuthStore } from '../stores/authStore';
import type { AffinityEvent } from '../features/affinity/events';
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
});

const rulesFixture: FlowRulesResponse = {
  version: 'test-1',
  flowRules: [
    {
      fromCategoryCode: 'C001001',
      fromTagSlug: null,
      toCategoryCode: 'C001002',
      toTagSlug: 'moisture',
      edgeKind: 'NEXT_STEP',
      reason: '결을 정돈했다면 영양을 채울 차례예요',
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

/** 실제로 나간 GET /goods 요청의 (categoryCode, tag) 조합. 개인화가 쿼리에 닿았는지 여기서 본다. */
let goodsCalls: { categoryCode: string | null; tag: string | null; size: string | null }[] = [];

/**
 * /goods를 테스트가 통제한다 — 기본 픽스처는 (카테고리 × 태그) 조합마다 상품이 4개씩 있지는
 * 않아서, 폴백이 걸렸는지 개인화가 살아남았는지를 구분할 수 없다.
 * `countFor`가 그 조합의 후보 수를 결정한다.
 */
function serveGoods(countFor: (tag: string | null) => number) {
  server.use(
    http.get('/api/v1/goods', ({ request }) => {
      const url = new URL(request.url);
      const tag = url.searchParams.get('tag');
      goodsCalls.push({
        categoryCode: url.searchParams.get('categoryCode'),
        tag,
        size: url.searchParams.get('size'),
      });

      const count = countFor(tag);
      const content = Array.from({ length: count }, (_, i) => ({
        goodsNo: 1000 + i,
        brandName: '브랜드',
        name: `${tag ?? '기본'} 상품 ${i + 1}`,
        thumbnailUrl: '',
        listPrice: 10000,
        salePrice: 10000,
        discountRate: 0,
        badges: [],
        rating: 0,
        reviewCount: 0,
        wished: false,
        todayDreamAvailable: false,
        tags: [],
      }));

      return HttpResponse.json({
        code: 'OK',
        message: 'success',
        data: {
          content,
          page: 0,
          size: count,
          totalElements: count,
          totalPages: 1,
          hasNext: false,
        },
      });
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
      <MemoryRouter>
        <Main />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

/** 섹션 하나를 제목으로 집어온다 — reason은 그 섹션 안에서만 찾아야 옆 섹션과 헷갈리지 않는다. */
function sectionOf(label: string) {
  return screen.getByRole('region', { name: label });
}

beforeEach(() => {
  goodsCalls = [];
  localStorage.clear();
  useAuthStore.setState({ accessToken: null });
  serveRules(rulesFixture);
  serveGoods(() => 4);
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
});

describe('Main — 개인화', () => {
  it('프로필도 행동도 없으면 모든 섹션이 tag 없는 기본 쿼리를 부른다', async () => {
    renderMain();

    await waitFor(() => expect(goodsCalls).toHaveLength(ROUTINE_STEPS.length));
    expect(goodsCalls.every((call) => call.tag === null)).toBe(true);
    expect(goodsCalls.every((call) => call.size === '4')).toBe(true);
    expect(
      screen.queryByText('모공은 세럼 단계에서 정면으로 잡는 게 효율적이에요'),
    ).not.toBeInTheDocument();
  });

  it('프로필만 있으면 지정 STEP에 tag가 붙고 reason 문장이 렌더된다', async () => {
    serveMe({ skinType: 'COMBINATION', concerns: ['pore'] });

    renderMain();

    const serum = sectionOf('에센스/세럼');
    expect(
      await within(serum).findByText('모공은 세럼 단계에서 정면으로 잡는 게 효율적이에요'),
    ).toBeInTheDocument();

    // 개인화된 섹션만 tag가 붙고, tie-break 여유분(8)까지 받아 온다.
    const personalized = goodsCalls.find((call) => call.tag !== null);
    expect(personalized).toEqual({ categoryCode: 'C001002', tag: 'pore', size: '8' });

    // 나머지 네 단계는 기준선 그대로다 — 5단계가 전부 바뀌면 "다른 화면"으로 읽힌다.
    expect(within(sectionOf('로션/크림')).queryByText(/모공은/)).not.toBeInTheDocument();
  });

  it('행동 5건 이상이면 flowRules 매칭 결과가 우선한다', async () => {
    serveMe({ skinType: 'COMBINATION', concerns: ['pore'] });
    seedEvents(
      Array.from({ length: 5 }, (_, i) => ({
        goodsNo: i + 1,
        cat3: 'C001001',
        tags: ['soothe'],
        w: 1 as const,
      })),
    );

    renderMain();

    const serum = sectionOf('에센스/세럼');
    // 같은 STEP을 프로필 규칙(pore)도 겨냥하지만 티어2가 이긴다.
    expect(
      await within(serum).findByText('결을 정돈했다면 영양을 채울 차례예요'),
    ).toBeInTheDocument();
    expect(within(serum).queryByText(/모공은/)).not.toBeInTheDocument();
    expect(goodsCalls.find((call) => call.tag !== null)?.tag).toBe('moisture');
  });

  it('개인화 후보가 4개 미만이면 그 섹션만 기본 쿼리로 폴백하고 reason이 사라진다', async () => {
    serveMe({ skinType: 'COMBINATION', concerns: ['pore'] });
    // 태그가 붙은 쿼리만 후보가 모자란 상황.
    serveGoods((tag) => (tag === null ? 4 : 3));

    renderMain();

    // 개인화 쿼리(tag=pore, size=8)가 나갔고, 후보 3개라 같은 카테고리의 기본 쿼리로 되돌아온다.
    // (첫 렌더는 프로필이 아직 없어 기본 쿼리로 시작하므로 이 카테고리만 요청이 셋이 된다.)
    await waitFor(() =>
      expect(goodsCalls.filter((call) => call.categoryCode === 'C001002')).toHaveLength(3),
    );
    const serumCalls = goodsCalls.filter((call) => call.categoryCode === 'C001002');
    expect(serumCalls[1]).toEqual({ categoryCode: 'C001002', tag: 'pore', size: '8' });
    expect(serumCalls[2]).toEqual({ categoryCode: 'C001002', tag: null, size: '4' });

    await waitFor(() => expect(screen.queryByText(/모공은 세럼 단계에서/)).not.toBeInTheDocument());
    // 폴백된 섹션도 상품은 정상적으로 4개가 그려진다 — 빈 슬롯이 생기지 않는다.
    expect(within(sectionOf('에센스/세럼')).getAllByRole('link', { name: /기본 상품/ })).toHaveLength(
      4,
    );
  });

  it('flow-rules 요청이 실패해도 메인이 기본 화면으로 렌더된다', async () => {
    serveMe({ skinType: 'COMBINATION', concerns: ['pore'] });
    server.use(
      http.get('/api/v1/routine/flow-rules', () => new HttpResponse(null, { status: 500 })),
    );

    renderMain();

    await waitFor(() => expect(goodsCalls).toHaveLength(ROUTINE_STEPS.length));
    expect(goodsCalls.every((call) => call.tag === null)).toBe(true);
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(ROUTINE_STEPS.length);
  });
});

import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import type { SearchResultItem } from '../types/search';
import { Search } from './Search';

/**
 * 실서버 검색 결과 형태를 그대로 재현한다 — SearchResultItem.tags는 optional이고
 * 서버는 tags 필드 자체를 내려주지 않는다. `tags: []`로 위장하면 GoodsCard가
 * item.tags를 필수 배열로 접근해도 테스트가 녹색이 되어 실서버 크래시를 못 잡는다.
 */
const SAMPLE_ITEM: SearchResultItem = {
  goodsNo: 101,
  brandName: '어반메일',
  name: '수분 진정 토너',
  thumbnailUrl: 'data:image/svg+xml;utf8,<svg/>',
  listPrice: 20000,
  salePrice: 16000,
  discountRate: 20,
  badges: [],
  rating: 4.5,
  reviewCount: 12,
  wished: false,
  todayDreamAvailable: false,
};

function emptyPage() {
  return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false };
}

function mockMatchMedia() {
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
}

function renderSearch(initialEntry: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Search />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

/** fireEvent.change 여러 번으로 한 글자씩 타이핑하는 상황을 흉내낸다(user-event 미설치 환경). */
function typeInto(input: HTMLElement, text: string) {
  for (let i = 1; i <= text.length; i += 1) {
    fireEvent.change(input, { target: { value: text.slice(0, i) } });
  }
}

beforeEach(() => {
  mockMatchMedia();
  // 자동완성/인기 검색어는 기본적으로 무해한 값을 내려준다 — 각 테스트가 필요하면 덮어쓴다.
  server.use(
    http.get('/api/v1/search/autocomplete', () =>
      HttpResponse.json({ code: 'OK', message: '성공', data: [] }),
    ),
    http.get('/api/v1/search/popular-keywords', () =>
      HttpResponse.json({ code: 'OK', message: '성공', data: ['토너', '선크림', '클렌징폼'] }),
    ),
  );
});

afterEach(() => {
  vi.useRealTimers();
});

test('입력 후 300ms가 지나면 자동완성이 정확히 한 번 호출된다', async () => {
  let callCount = 0;
  server.use(
    http.get('/api/v1/search/autocomplete', ({ request }) => {
      callCount += 1;
      const url = new URL(request.url);
      const q = url.searchParams.get('q') ?? '';
      return HttpResponse.json({ code: 'OK', message: '성공', data: [`${q}1`, `${q}2`] });
    }),
  );

  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
  renderSearch('/search');

  const input = screen.getByLabelText('검색어');
  fireEvent.change(input, { target: { value: '토너' } });

  // 디바운스 타이머(300ms)만 가짜 시계로 발화시키고, 이후 실제 요청(msw)·응답 처리는
  // 실제 타이머로 되돌려 폴링(waitFor)이 멈추지 않게 한다.
  act(() => {
    vi.advanceTimersByTime(300);
  });
  vi.useRealTimers();

  await waitFor(() => expect(callCount).toBe(1));
});

test('빠르게 여러 글자를 입력해도 안정된 뒤 자동완성은 한 번만 호출된다(마지막 값 기준)', async () => {
  let callCount = 0;
  let lastQuery = '';
  server.use(
    http.get('/api/v1/search/autocomplete', ({ request }) => {
      callCount += 1;
      const url = new URL(request.url);
      lastQuery = url.searchParams.get('q') ?? '';
      return HttpResponse.json({ code: 'OK', message: '성공', data: [] });
    }),
  );

  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
  renderSearch('/search');

  const input = screen.getByLabelText('검색어');

  // 글자마다 100ms만 흘려보낸다 — 300ms 디바운스보다 짧으므로 매번 타이머가 취소·재예약돼야 한다.
  typeInto(input, '토');
  act(() => {
    vi.advanceTimersByTime(100);
  });
  typeInto(input, '토너');
  act(() => {
    vi.advanceTimersByTime(100);
  });

  expect(callCount).toBe(0);

  act(() => {
    vi.advanceTimersByTime(300);
  });
  vi.useRealTimers();

  await waitFor(() => expect(callCount).toBe(1));
  expect(lastQuery).toBe('토너');
});

test('검색 결과가 0건이면 EmptyState를 보여주고 그리드는 렌더하지 않으며 인기 검색어를 보여준다', async () => {
  server.use(
    http.get('/api/v1/search', () =>
      HttpResponse.json({ code: 'OK', message: '성공', data: emptyPage() }),
    ),
  );

  renderSearch('/search?q=존재하지않는상품');

  expect(await screen.findByText(/에 대한 검색 결과가 없어요/)).toBeInTheDocument();
  expect(screen.getAllByRole('status').length).toBeGreaterThan(0);
  expect(screen.queryByText('표시할 상품이 없어요')).not.toBeInTheDocument();

  // 인기 검색어가 클릭 가능한 칩으로 노출된다.
  expect(await screen.findByRole('button', { name: '토너' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '선크림' })).toBeInTheDocument();
});

test('빈 검색어(진입 시)면 검색을 실행하지 않고 인기 검색어만 보여준다', async () => {
  renderSearch('/search');

  expect(await screen.findByRole('button', { name: '토너' })).toBeInTheDocument();
  expect(screen.queryByText(/검색 결과/)).not.toBeInTheDocument();
});

test('딥링크(/search?q=)로 들어오면 진입 즉시 검색이 실행되어 결과가 렌더된다', async () => {
  server.use(
    http.get('/api/v1/search', ({ request }) => {
      const url = new URL(request.url);
      const q = url.searchParams.get('q');
      return HttpResponse.json({
        code: 'OK',
        message: '성공',
        data:
          q === '토너'
            ? { content: [SAMPLE_ITEM], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false }
            : emptyPage(),
      });
    }),
  );

  renderSearch('/search?q=토너');

  expect(await screen.findByText(SAMPLE_ITEM.name)).toBeInTheDocument();
});

test('딥링크(/search?q=)로 들어오면 자동완성 오버레이는 열리지 않고, 타이핑해야만 300ms 뒤에 열린다', async () => {
  let autocompleteCallCount = 0;
  server.use(
    http.get('/api/v1/search', ({ request }) => {
      const url = new URL(request.url);
      const q = url.searchParams.get('q');
      return HttpResponse.json({
        code: 'OK',
        message: '성공',
        data:
          q === '토너'
            ? { content: [SAMPLE_ITEM], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false }
            : emptyPage(),
      });
    }),
    http.get('/api/v1/search/autocomplete', ({ request }) => {
      autocompleteCallCount += 1;
      const url = new URL(request.url);
      const q = url.searchParams.get('q') ?? '';
      return HttpResponse.json({ code: 'OK', message: '성공', data: [`${q}1`, `${q}2`] });
    }),
  );

  renderSearch('/search?q=토너');

  // 진입 직후 결과는 렌더되지만, 사용자가 타이핑한 적이 없으므로 오버레이는 열리면 안 된다.
  expect(await screen.findByText(SAMPLE_ITEM.name)).toBeInTheDocument();
  expect(document.querySelector('[role=listbox]')).toBeNull();

  // 300ms(디바운스 구간)를 실제로 흘려보내도 자동완성 요청 자체가 나가지 않아야 한다.
  await new Promise((resolve) => setTimeout(resolve, 350));
  expect(document.querySelector('[role=listbox]')).toBeNull();
  expect(autocompleteCallCount).toBe(0);

  // 이후 사용자가 실제로 타이핑하면 여전히 정상 동작한다(오버레이가 300ms 뒤에 열린다).
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
  const input = screen.getByLabelText('검색어');
  fireEvent.change(input, { target: { value: '토너2' } });
  act(() => {
    vi.advanceTimersByTime(300);
  });
  vi.useRealTimers();

  await waitFor(() => expect(document.querySelector('[role=listbox]')).not.toBeNull());
});

test('검색어를 입력해 제출하면 ?q=가 갱신되고 결과가 렌더된다', async () => {
  server.use(
    http.get('/api/v1/search', ({ request }) => {
      const url = new URL(request.url);
      const q = url.searchParams.get('q');
      return HttpResponse.json({
        code: 'OK',
        message: '성공',
        data:
          q === '토너'
            ? { content: [SAMPLE_ITEM], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false }
            : emptyPage(),
      });
    }),
  );

  renderSearch('/search');

  const input = screen.getByLabelText('검색어');
  typeInto(input, '토너');
  fireEvent.click(screen.getByRole('button', { name: '검색' }));

  expect(await screen.findByText(SAMPLE_ITEM.name)).toBeInTheDocument();
});

test('자동완성 제안을 선택하면 그 값으로 ?q=가 갱신되고 결과가 렌더된다', async () => {
  server.use(
    http.get('/api/v1/search/autocomplete', () =>
      HttpResponse.json({ code: 'OK', message: '성공', data: ['수분 진정 토너'] }),
    ),
    http.get('/api/v1/search', ({ request }) => {
      const url = new URL(request.url);
      const q = url.searchParams.get('q');
      return HttpResponse.json({
        code: 'OK',
        message: '성공',
        data:
          q === '수분 진정 토너'
            ? { content: [SAMPLE_ITEM], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false }
            : emptyPage(),
      });
    }),
  );

  renderSearch('/search');

  const input = screen.getByLabelText('검색어');
  typeInto(input, '토너');

  const option = await screen.findByRole('option', { name: '수분 진정 토너' });
  fireEvent.mouseDown(option);

  expect(await screen.findByText(SAMPLE_ITEM.name)).toBeInTheDocument();
});

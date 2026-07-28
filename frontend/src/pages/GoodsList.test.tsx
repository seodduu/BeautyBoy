import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation, type Location } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { GoodsList } from './GoodsList';

/** 목 서버가 마지막으로 받은 /goods 요청의 searchParams — 배선 테스트의 관측 지점. */
let capturedSearchParams: URLSearchParams | null = null;
/** MemoryRouter 내부의 현재 위치 — "URL이 상태의 진실" 단언용. */
let location: Location | null = null;
/** jsdom은 scrollIntoView 미구현 — 스텁이 곧 "페이지 전환 시 목록 상단으로" 관측 지점이다. */
let scrollIntoView = vi.fn();

beforeEach(() => {
  capturedSearchParams = null;
  location = null;
  scrollIntoView = vi.fn();
  Element.prototype.scrollIntoView = scrollIntoView;
  // 응답은 기본 핸들러에 넘기고(undefined 반환 → fall-through) 요청 파라미터만 기록한다.
  server.use(
    http.get('/api/v1/goods', ({ request }) => {
      capturedSearchParams = new URL(request.url).searchParams;
      return undefined;
    }),
  );
});

function LocationProbe() {
  location = useLocation();
  return null;
}

function currentLocation(): Location {
  if (!location) throw new Error('LocationProbe가 아직 렌더되지 않았다');
  return location;
}

function renderList(search: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/goods${search}`]}>
        <GoodsList />
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('GoodsList — 카테고리 목록', () => {
  it('루틴 단계 코드면 그 단계 이름을 제목으로 쓴다', async () => {
    renderList('?category=C002');

    expect(await screen.findByRole('heading', { name: '클렌징', level: 1 })).toBeInTheDocument();
  });

  it('category 쿼리로 필터한 결과 전체를 보여준다', async () => {
    renderList('?category=C002');

    // C002는 fixture에 8건(C002001:3 + C002002:3 + C002003:2)
    await waitFor(() => {
      expect(screen.getAllByRole('link', { name: /No\./ })).toHaveLength(8);
    });
  });

  it('category가 없으면 전체 상품을 보여준다', async () => {
    renderList('');

    expect(await screen.findByRole('heading', { name: '전체 상품', level: 1 })).toBeInTheDocument();
  });

  it('결과가 0건이면 빈 상태를 보여준다', async () => {
    renderList('?category=C999');

    expect(await screen.findByText('표시할 상품이 없어요')).toBeInTheDocument();
  });

  it('결과가 0건이면 상품 개수 문구는 찍지 않는다', async () => {
    renderList('?category=C999');

    await screen.findByText('표시할 상품이 없어요');
    expect(screen.queryByText(/개의 상품/)).not.toBeInTheDocument();
  });

  it('tag 쿼리로 진입하면 그 태그를 가진 상품만 보여준다', async () => {
    renderList('?tag=uv');

    // 태그확장(V72) 샘플 추가로 fixture TAG_PLAN은 7개 패턴이 순환하고 'uv'는 그중 인덱스 5 —
    // goodsFixtures 40건 중 (goodsNo-1) % 7 === 5인 5건(6·13·20·27·34)만 해당 태그를 갖는다.
    expect(await screen.findByRole('heading', { name: '태그 상품', level: 1 })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getAllByRole('link', { name: /No\./ })).toHaveLength(5);
    });
  });

  it('sort 쿼리 파라미터가 fetch에 그대로 전달되고 URL이 상태의 진실이다', async () => {
    renderList('?category=C002&sort=priceAsc');
    await screen.findByRole('combobox', { name: '정렬' });
    await waitFor(() => {
      expect(capturedSearchParams?.get('sort')).toBe('priceAsc');
    });
  });

  it('정렬 변경은 setSearchParams로 URL을 바꾼다 — 기본값 popular은 파라미터를 지운다', async () => {
    renderList('?category=C002&sort=priceAsc');
    fireEvent.change(await screen.findByRole('combobox', { name: '정렬' }), {
      target: { value: 'popular' },
    });
    expect(currentLocation().search).not.toContain('sort=');
  });

  it('sort 미지값은 popular로 정규화해 서버 400을 막는다', async () => {
    renderList('?category=C002&sort=weird');
    const select = await screen.findByRole('combobox', { name: '정렬' });
    expect(select).toHaveValue('popular');
    await waitFor(() => {
      expect(capturedSearchParams?.get('sort')).not.toBe('weird');
    });
  });

  it('sort=review가 fetch에 전달된다 — 리뷰 많은 순은 서버 정렬이다', async () => {
    renderList('?category=C002&sort=review');
    await screen.findByRole('combobox', { name: '정렬' });
    await waitFor(() => {
      expect(capturedSearchParams?.get('sort')).toBe('review');
    });
  });

  it('가격대 pill 선택 시 minPrice/maxPrice가 실제 요청에 실린다', async () => {
    renderList('?category=C002&price=FROM_10K_TO_30K');
    await screen.findByRole('combobox', { name: '정렬' });
    await waitFor(() => {
      expect(capturedSearchParams?.get('minPrice')).toBe('10000');
    });
    expect(capturedSearchParams?.get('maxPrice')).toBe('29999');
  });

  it('가격대 pill 토글은 URL의 price 파라미터를 바꾸고, 선택 상태로 반영된다', async () => {
    renderList('?category=C002&price=UNDER_10K');
    await screen.findByRole('combobox', { name: '정렬' });
    expect(screen.getByRole('button', { name: '1만원 미만' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );

    fireEvent.click(screen.getByRole('button', { name: '3만원 이상' }));
    expect(currentLocation().search).toContain('price=OVER_30K');

    fireEvent.click(screen.getByRole('button', { name: '3만원 이상' }));
    expect(currentLocation().search).not.toContain('price=');
  });

  // ---- 페이지네이션 (T2-B) ----
  // mock 픽스처는 40건이고 한 페이지가 20건이라 카테고리 없이 들어가야 2페이지가 생긴다
  // (실 시드는 전체 190·C001 67건 — 계획서의 C001 예시는 픽스처 규모에 맞춰 옮겼다).

  it('?page=2는 0-based page=1로 요청되고, 페이저가 렌더된다', async () => {
    renderList('?page=2');
    await screen.findByRole('navigation', { name: '페이지 이동' });
    await waitFor(() => {
      expect(capturedSearchParams?.get('page')).toBe('1');
    });
  });

  it('한 페이지에 다 들어가면 페이저를 렌더하지 않는다', async () => {
    renderList('?category=C002');
    await screen.findByRole('combobox', { name: '정렬' });
    await waitFor(() => {
      expect(screen.getAllByRole('link', { name: /No\./ })).toHaveLength(8);
    });
    expect(screen.queryByRole('navigation', { name: '페이지 이동' })).not.toBeInTheDocument();
  });

  it('page 미지값·0 이하는 1로 정규화한다 — 손으로 친 URL이 빈 화면이 되지 않게', async () => {
    renderList('?page=0');
    await screen.findByRole('combobox', { name: '정렬' });
    await waitFor(() => {
      expect(capturedSearchParams?.get('page')).toBe('0');
    });
  });

  it('페이지를 넘기면 URL이 바뀌고, 1페이지로 돌아오면 page 파라미터가 지워진다', async () => {
    renderList('?page=2');
    fireEvent.click(await screen.findByRole('button', { name: '1' }));
    expect(currentLocation().search).not.toContain('page=');
  });

  it('페이지를 넘기면 목록 상단(툴바)으로 즉시 스크롤한다 — 문서 최상단이 아니다', async () => {
    renderList('?page=2');
    fireEvent.click(await screen.findByRole('button', { name: '1' }));
    expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'instant', block: 'start' });
  });

  it('정렬이 바뀌면 page는 1로 리셋된다 — 결과 집합이 달라졌는데 2페이지에 남는 것은 거짓 화면이다', async () => {
    renderList('?page=2');
    fireEvent.change(await screen.findByRole('combobox', { name: '정렬' }), {
      target: { value: 'priceAsc' },
    });
    expect(currentLocation().search).not.toContain('page=');
  });

  it('가격대가 바뀌어도 page는 1로 리셋된다', async () => {
    renderList('?page=2');
    await screen.findByRole('combobox', { name: '정렬' });
    fireEvent.click(screen.getByRole('button', { name: '3만원 이상' }));
    expect(currentLocation().search).not.toContain('page=');
  });

  it('조회가 실패하면 에러 문구를 보여주고 개수·빈 상태는 보여주지 않는다', async () => {
    server.use(http.get('/api/v1/goods', () => new HttpResponse(null, { status: 500 })));

    renderList('?category=C002');

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('상품을 불러오지 못했어요')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
    expect(screen.queryByText(/개의 상품/)).not.toBeInTheDocument();
    expect(screen.queryByText('표시할 상품이 없어요')).not.toBeInTheDocument();
  });
});

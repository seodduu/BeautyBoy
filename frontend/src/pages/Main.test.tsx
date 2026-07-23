import { beforeAll, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Main } from './Main';
import { ROUTINE_STEPS } from '../features/routine/steps';

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

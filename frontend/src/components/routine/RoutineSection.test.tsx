import { beforeAll, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '../ui/ToastProvider';
import { RoutineSection } from './RoutineSection';
import { ROUTINE_STEPS } from '../../features/routine/steps';
import type { StepComposition } from '../../features/affinity/composer';
import type { GoodsListItem } from '../../types/goods';

// PickCard가 useToast를 쓰고, ToastProvider는 prefers-reduced-motion 판정에 matchMedia를 쓴다.
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

const cleansing = ROUTINE_STEPS[0];

function goods(goodsNo: number): GoodsListItem {
  return {
    goodsNo,
    brandName: '브랜드',
    name: `상품 ${goodsNo}`,
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
  };
}

function composition(partial: Partial<StepComposition> = {}): StepComposition {
  return {
    pick: goods(1),
    alternatives: [goods(2), goods(3), goods(4)],
    reason: null,
    matched: { concerns: [], behaviors: [] },
    ...partial,
  };
}

function renderSection(props: Partial<React.ComponentProps<typeof RoutineSection>> = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>
          <RoutineSection step={cleansing} index={0} {...props} />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('RoutineSection', () => {
  it('단계 순번·이름·카피를 보여준다', () => {
    renderSection();

    expect(screen.getByText('STEP 01')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '클렌징' })).toBeInTheDocument();
    expect(screen.getByText(cleansing.copy)).toBeInTheDocument();
  });

  it('앵커 대상이 되도록 섹션에 단계 id를 단다', () => {
    const { container } = renderSection();

    expect(container.querySelector('section')).toHaveAttribute('id', 'cleansing');
  });

  it('단계 이미지를 레포 내부 경로로 렌더한다', () => {
    renderSection();

    const image = screen.getByRole('img', { name: /클렌징/ });
    expect(image).toHaveAttribute('src', '/images/routine/01-cleansing.jpg');
  });

  it('조합이 미확정이면 스켈레톤을 유지한다 — 점진 렌더', () => {
    const { container } = renderSection({ composition: undefined });

    expect(container.querySelectorAll('.bb-goods-card-skeleton').length).toBeGreaterThan(0);
    expect(screen.queryByRole('heading', { level: 3 })).not.toBeInTheDocument();
  });

  it('픽이 있으면 픽 카드와 대안 3개를 그린다', () => {
    renderSection({ composition: composition() });

    expect(screen.getByRole('heading', { level: 3 })).toHaveTextContent('상품 1');
    for (const goodsNo of [2, 3, 4]) {
      expect(screen.getByText(`상품 ${goodsNo}`)).toBeInTheDocument();
    }
  });

  it('픽이 null이면(풀 비었음·게이트 전원 탈락) 기준선 그리드로 폴백한다', () => {
    renderSection({
      composition: composition({ pick: null, alternatives: [] }),
      pool: [goods(7), goods(8)],
    });

    expect(screen.queryByRole('heading', { level: 3 })).not.toBeInTheDocument();
    expect(screen.getByText('상품 7')).toBeInTheDocument();
    expect(screen.getByText('상품 8')).toBeInTheDocument();
  });

  it('더보기가 해당 카테고리 목록으로 연결된다', () => {
    renderSection();

    const more = screen.getByRole('link', { name: '클렌징 전체 보기' });
    expect(more).toHaveAttribute('href', '/goods?category=C002');
  });

  it('풀 조회가 실패하면 에러 문구를 보여주고 빈 상태 문구는 보여주지 않는다', () => {
    renderSection({ isError: true });

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('상품을 불러오지 못했어요')).toBeInTheDocument();
    expect(screen.queryByText('표시할 상품이 없어요')).not.toBeInTheDocument();
  });

  it('짝수 index는 타이포가 왼쪽, 홀수 index는 오른쪽에 온다', () => {
    const { container: even } = renderSection({ index: 0 });
    expect(even.querySelector('section')).toHaveClass('bb-routine--text-left');

    const { container: odd } = renderSection({ index: 1, step: ROUTINE_STEPS[1] });
    expect(odd.querySelector('section')).toHaveClass('bb-routine--text-right');
  });
});

import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { RoutineSection } from './RoutineSection';
import { ROUTINE_STEPS } from '../../features/routine/steps';

const cleansing = ROUTINE_STEPS[0];

function renderSection(index = 0, step = cleansing) {
  // 테스트마다 새 QueryClient — 캐시가 테스트 간에 새지 않게. retry는 끈다(실패를 즉시 드러낸다).
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <RoutineSection step={step} index={index} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('RoutineSection', () => {
  it('단계 순번·이름·카피를 보여준다', async () => {
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
    expect(image).toHaveAttribute('src', '/images/routine/01-cleansing.svg');
  });

  it('해당 카테고리 상품을 4개까지 보여준다', async () => {
    renderSection();

    // C002(클렌징)는 fixture에 8건 있지만 섹션은 4개만 노출한다.
    await waitFor(() => {
      expect(screen.getAllByRole('link', { name: /No\./ })).toHaveLength(4);
    });
  });

  it('더보기가 해당 카테고리 목록으로 연결된다', () => {
    renderSection();

    const more = screen.getByRole('link', { name: '클렌징 전체 보기' });
    expect(more).toHaveAttribute('href', '/goods?category=C002');
  });

  it('상품 조회가 실패하면 에러 문구를 보여주고 빈 상태 문구는 보여주지 않는다', async () => {
    server.use(http.get('/api/v1/goods', () => new HttpResponse(null, { status: 500 })));

    renderSection();

    expect(
      await screen.findByText('상품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'),
    ).toBeInTheDocument();
    expect(screen.queryByText('표시할 상품이 없어요')).not.toBeInTheDocument();
  });

  it('짝수 index는 타이포가 왼쪽, 홀수 index는 오른쪽에 온다', () => {
    const { container: even } = renderSection(0);
    expect(even.querySelector('section')).toHaveClass('bb-routine--text-left');

    const { container: odd } = renderSection(1, ROUTINE_STEPS[1]);
    expect(odd.querySelector('section')).toHaveClass('bb-routine--text-right');
  });
});

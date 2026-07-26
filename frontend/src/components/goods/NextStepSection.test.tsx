import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NextStepSection } from './NextStepSection';

// 테스트마다 새 QueryClient — 캐시가 테스트 간에 새지 않게. retry는 끈다(실패를 즉시 드러낸다).
function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe('NextStepSection', () => {
  it('블록마다 이유 문장과 상품 그리드를 그린다', async () => {
    render(<NextStepSection goodsNo={2} />, { wrapper });
    expect(
      await screen.findByText('각질 토너 다음 단계는 진정 세럼으로 완충하세요'),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '다음 단계' })).toBeInTheDocument();
  });

  it('blocks가 비면 아무것도 그리지 않는다', async () => {
    const { container } = render(<NextStepSection goodsNo={6} />, { wrapper }); // MSW가 빈 blocks 반환하는 상품
    await waitFor(() => expect(container.querySelector('.bb-next-step')).not.toBeInTheDocument());
  });
});

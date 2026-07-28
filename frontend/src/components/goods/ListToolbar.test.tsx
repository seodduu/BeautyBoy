import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ListToolbar } from './ListToolbar';

// 계획서 원문은 @testing-library/user-event를 쓰지만 이 프로젝트는 미설치 환경이라
// (Search.test.tsx 관례) fireEvent로 같은 상호작용을 낸다. 단언은 계획서 그대로.
function renderToolbar(props: Partial<Parameters<typeof ListToolbar>[0]> = {}) {
  const onSortChange = vi.fn();
  const onPriceBandChange = vi.fn();
  render(
    <MemoryRouter>
      <ListToolbar
        category="C002"
        sort="popular"
        priceBand={null}
        onSortChange={onSortChange}
        onPriceBandChange={onPriceBandChange}
        {...props}
      />
    </MemoryRouter>,
  );
  return { onSortChange, onPriceBandChange };
}

describe('ListToolbar', () => {
  it('정렬 6종을 서버 GoodsSort 값으로 노출한다', () => {
    renderToolbar();
    const select = screen.getByRole('combobox', { name: '정렬' });
    const values = Array.from(select.querySelectorAll('option')).map((o) => o.getAttribute('value'));
    expect(values).toEqual(['popular', 'new', 'sales', 'priceAsc', 'discount', 'review']);
  });

  it('정렬을 바꾸면 onSortChange가 서버 값으로 불린다', () => {
    const { onSortChange } = renderToolbar();
    fireEvent.change(screen.getByRole('combobox', { name: '정렬' }), {
      target: { value: 'priceAsc' },
    });
    expect(onSortChange).toHaveBeenCalledWith('priceAsc');
  });

  it('루틴 5단계 탭을 렌더하고 현재 카테고리에 aria-current를 단다', () => {
    renderToolbar({ category: 'C002' });
    const current = screen.getByRole('link', { name: '클렌징' });
    expect(current).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: '선크림' })).toHaveAttribute('href', '/goods?category=C004001');
  });

  it('루틴 단계 밖 카테고리면 탭을 렌더하지 않는다', () => {
    renderToolbar({ category: 'C003' });
    expect(screen.queryByRole('link', { name: '클렌징' })).not.toBeInTheDocument();
  });

  it('가격대 pill을 토글하면 onPriceBandChange가 불리고, 선택된 pill을 다시 누르면 해제(null)된다', () => {
    const { onPriceBandChange } = renderToolbar({ priceBand: 'UNDER_10K' });
    fireEvent.click(screen.getByRole('button', { name: '1만원 미만' }));
    expect(onPriceBandChange).toHaveBeenCalledWith(null);
    fireEvent.click(screen.getByRole('button', { name: '3만원 이상' }));
    expect(onPriceBandChange).toHaveBeenCalledWith('OVER_30K');
  });
});

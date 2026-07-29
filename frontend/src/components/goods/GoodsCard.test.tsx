import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { GoodsCard } from './GoodsCard';
import { WEIGHT, readEvents } from '../../features/affinity/events';
import type { GoodsListItem } from '../../types/goods';

const baseItem: GoodsListItem = {
  goodsNo: 1,
  brandName: '브랜드',
  name: '상품명',
  thumbnailUrl: '/thumb.jpg',
  listPrice: 10000,
  salePrice: 8000,
  discountRate: 20,
  badges: [],
  rating: 4.3,
  reviewCount: 12,
  wished: false,
  todayDreamAvailable: false,
  tags: [],
};

function renderCard(item: GoodsListItem, onWishToggle = vi.fn()) {
  return render(
    <MemoryRouter>
      <GoodsCard item={item} onWishToggle={onWishToggle} />
    </MemoryRouter>,
  );
}

describe('GoodsCard', () => {
  it('배지 4종이 모두 있는 아이템은 배지 4개(SALE/COUPON/GIFT/1+1)를 렌더한다', () => {
    renderCard({
      ...baseItem,
      badges: ['SALE', 'COUPON', 'GIFT', 'ONE_PLUS_ONE'],
      todayDreamAvailable: true,
    });

    expect(screen.getByText('SALE')).toBeInTheDocument();
    expect(screen.getByText('COUPON')).toBeInTheDocument();
    expect(screen.getByText('GIFT')).toBeInTheDocument();
    expect(screen.getByText('1+1')).toBeInTheDocument();
    expect(screen.getByText('오늘드림')).toBeInTheDocument();
  });

  it('discountRate=0이면 정가 취소선(정가 노드)이 없다', () => {
    renderCard({ ...baseItem, discountRate: 0 });

    expect(screen.queryByText('10,000원')).not.toBeInTheDocument();
    expect(screen.getByText('8,000원')).toBeInTheDocument();
  });

  it('wished=true면 하트가 aria-pressed=true이고, 클릭하면 onWishToggle(goodsNo, 직전 찜 여부)가 호출된다', () => {
    const onWishToggle = vi.fn();
    renderCard({ ...baseItem, wished: true }, onWishToggle);

    const wishButton = screen.getByRole('button', { name: /찜/ });
    expect(wishButton).toHaveAttribute('aria-pressed', 'true');

    fireEvent.click(wishButton);
    expect(onWishToggle).toHaveBeenCalledWith(baseItem.goodsNo, true);
  });

  it('찜 버튼 클릭이 링크 네비게이션(카드 링크)을 트리거하지 않는다', () => {
    const onWishToggle = vi.fn();
    renderCard({ ...baseItem, wished: false }, onWishToggle);

    const wishButton = screen.getByRole('button', { name: /찜/ });
    const clickEvent = new MouseEvent('click', { bubbles: true, cancelable: true });
    const stopPropagationSpy = vi.spyOn(clickEvent, 'stopPropagation');

    fireEvent(wishButton, clickEvent);

    expect(stopPropagationSpy).toHaveBeenCalled();
  });

  it('rating=0, reviewCount=0이면 "첫 리뷰를 기다려요"가 뜨고 카드가 정상 렌더된다', () => {
    renderCard({ ...baseItem, rating: 0, reviewCount: 0 });

    expect(screen.getByText('첫 리뷰를 기다려요')).toBeInTheDocument();
    expect(screen.getByText('상품명')).toBeInTheDocument();
  });

  it('상품명이 매우 길어도 카드가 렌더되고 2줄 고정 말줄임 클래스가 적용된다', () => {
    const longName =
      '아주 아주 아주 아주 아주 아주 아주 아주 아주 아주 아주 긴 상품명입니다 계속 길어집니다';
    renderCard({ ...baseItem, name: longName });

    const nameEl = screen.getByText(longName);
    expect(nameEl).toHaveClass('bb-goods-card__name');
  });

  it('카드 전체가 /goods/{goodsNo} 링크다', () => {
    renderCard(baseItem);
    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/goods/1');
  });

  it('효과(EFFECT) 태그는 최대 2개까지만 보이고 사용감(TEXTURE) 태그는 카드에 노출하지 않는다', () => {
    const { container } = renderCard({
      ...baseItem,
      tags: [
        { name: '진정', kind: 'EFFECT', slug: 'soothing' },
        { name: '보습', kind: 'EFFECT', slug: 'moisture' },
        { name: '세정', kind: 'EFFECT', slug: 'cleanse' },
        { name: '산뜻함', kind: 'TEXTURE', slug: 'fresh' },
      ],
    });

    const tags = container.querySelectorAll('.bb-tag');
    expect(tags.length).toBe(2);
    expect(tags[0]).toHaveTextContent('진정');
    expect(tags[1]).toHaveTextContent('보습');
  });

  it('tags가 빈 배열이면 태그 줄을 렌더하지 않는다', () => {
    const { container } = renderCard(baseItem);
    expect(container.querySelector('.bb-goods-card__tags')).not.toBeInTheDocument();
  });

  it('tags 필드 자체가 없어도(실서버 검색 결과 형태) 크래시 없이 렌더되고 태그 줄이 안 나온다', () => {
    const { tags: _tags, ...itemWithoutTags } = baseItem;
    const { container } = render(
      <MemoryRouter>
        <GoodsCard item={itemWithoutTags} onWishToggle={vi.fn()} />
      </MemoryRouter>,
    );

    expect(container.querySelector('.bb-goods-card__tags')).not.toBeInTheDocument();
    expect(screen.getByText('상품명')).toBeInTheDocument();
  });

  describe('찜 → 관심 이벤트 기록(설계 §6.1)', () => {
    beforeEach(() => {
      localStorage.clear();
    });

    it('categoryCode를 받으면 찜 클릭을 2점 이벤트로 기록한다', () => {
      const item = {
        ...baseItem,
        tags: [{ name: '보습', kind: 'EFFECT', slug: 'moisture' } as const],
      };
      render(
        <MemoryRouter>
          <GoodsCard item={item} onWishToggle={vi.fn()} categoryCode="C001002001" />
        </MemoryRouter>,
      );

      fireEvent.click(screen.getByRole('button', { name: '찜하기' }));

      // leaf 10자가 중분류 7자로 절단돼 규칙의 category_code와 같은 축이 된다.
      expect(readEvents()).toEqual([
        { goodsNo: 1, cat3: 'C001002', tags: ['moisture'], w: WEIGHT.wish },
      ]);
    });

    it('categoryCode가 없으면 기록하지 않는다 — 문맥 없는 찜(검색·추천 레일)', () => {
      renderCard(baseItem);

      fireEvent.click(screen.getByRole('button', { name: '찜하기' }));

      expect(readEvents()).toEqual([]);
    });

    it('기록 여부와 무관하게 onWishToggle은 항상 호출된다', () => {
      const onWishToggle = vi.fn();
      renderCard(baseItem, onWishToggle);

      fireEvent.click(screen.getByRole('button', { name: '찜하기' }));

      // 두 번째 인자는 누르기 직전의 찜 여부다 — 받는 쪽이 POST/DELETE를 고르는 근거.
      expect(onWishToggle).toHaveBeenCalledWith(1, false);
    });

    it('이미 찜한 카드는 직전 상태 true를 함께 넘긴다', () => {
      const onWishToggle = vi.fn();
      renderCard({ ...baseItem, wished: true }, onWishToggle);

      fireEvent.click(screen.getByRole('button', { name: '찜 해제' }));

      expect(onWishToggle).toHaveBeenCalledWith(1, true);
    });
  });
});

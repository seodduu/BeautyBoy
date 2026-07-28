import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Rating } from './Rating';

describe('Rating', () => {
  it('리뷰가 있으면 별 글리프 1개 + 소수 1자리 점수 + 리뷰수를 보여준다', () => {
    render(<Rating rating={4} reviewCount={3} />);
    const rating = screen.getByText((_, el) => el?.classList.contains('bb-rating') ?? false);
    expect(rating).toHaveTextContent('★ 4.0 (3)');
  });

  it('별 글리프는 장식이므로 스크린리더에서 숨긴다', () => {
    render(<Rating rating={4} reviewCount={3} />);
    expect(screen.getByText('★')).toHaveAttribute('aria-hidden', 'true');
  });

  it('리뷰가 없으면 "첫 리뷰를 기다려요"를 보여준다 — "리뷰 없음"은 쓰지 않는다', () => {
    render(<Rating rating={0} reviewCount={0} />);
    expect(screen.getByText('첫 리뷰를 기다려요')).toBeInTheDocument();
    expect(screen.queryByText('리뷰 없음')).not.toBeInTheDocument();
  });
});

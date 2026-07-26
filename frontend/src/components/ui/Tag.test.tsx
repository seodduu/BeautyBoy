import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Tag } from './Tag';

describe('Tag', () => {
  it('EFFECT는 무채색, TEXTURE는 회색 클래스', () => {
    const { rerender, container } = render(<Tag view={{ name: '세정', kind: 'EFFECT', slug: 'cleanse' }} />);
    expect(container.querySelector('.bb-tag')).toHaveClass('bb-tag--effect');
    rerender(<Tag view={{ name: '산뜻함', kind: 'TEXTURE', slug: 'fresh' }} />);
    expect(container.querySelector('.bb-tag')).toHaveClass('bb-tag--texture');
  });

  it('숫자·주황 주의색을 쓰지 않는다', () => {
    const { container } = render(<Tag view={{ name: '세정', kind: 'EFFECT', slug: 'cleanse' }} />);
    expect(container.textContent).toBe('세정');
  });

  it('to가 있으면 Link로 렌더한다', () => {
    const { container } = render(
      <MemoryRouter>
        <Tag view={{ name: '세정', kind: 'EFFECT', slug: 'cleanse' }} to="/goods?tag=cleanse" />
      </MemoryRouter>,
    );
    const link = container.querySelector('a.bb-tag');
    expect(link).toHaveAttribute('href', '/goods?tag=cleanse');
  });

  it('to가 없으면 span으로 렌더한다', () => {
    const { container } = render(<Tag view={{ name: '세정', kind: 'EFFECT', slug: 'cleanse' }} />);
    expect(container.querySelector('span.bb-tag')).toBeInTheDocument();
    expect(container.querySelector('a.bb-tag')).not.toBeInTheDocument();
  });

  it('slug별 컬러 클래스(bb-tag--{slug})가 붙는다', () => {
    const { container } = render(<Tag view={{ name: '보습', kind: 'EFFECT', slug: 'moisture' }} />);
    expect(container.querySelector('.bb-tag')).toHaveClass('bb-tag--moisture');
  });

  it('PROPERTY(gentle)도 slug 컬러 클래스를 받는다 — 폴백이 아니다', () => {
    const { container } = render(<Tag view={{ name: '저자극', kind: 'PROPERTY', slug: 'gentle' }} />);
    expect(container.querySelector('.bb-tag')).toHaveClass('bb-tag--gentle');
  });

  it('팔레트 미정의 slug는 무채색 폴백 클래스(bb-tag--effect)를 함께 받아 CSS가 그 값으로 떨어진다', () => {
    // slug 클래스(bb-tag--unknown-slug)는 항상 붙지만 Tag.css에 정의가 없으므로 스타일링 효과가 없다.
    // 무채색 유지의 실제 근거는 kind 클래스(bb-tag--effect)가 여전히 붙어 있다는 것이다.
    const { container } = render(<Tag view={{ name: '미정의', kind: 'EFFECT', slug: 'unknown-slug' }} />);
    const el = container.querySelector('.bb-tag')!;
    expect(el).toHaveClass('bb-tag--effect');
    expect(el).toHaveClass('bb-tag--unknown-slug');
  });

  it('TEXTURE는 slug와 무관하게 항상 무채색 폴백이다', () => {
    const { container } = render(<Tag view={{ name: '산뜻함', kind: 'TEXTURE', slug: 'fresh' }} />);
    const el = container.querySelector('.bb-tag')!;
    expect(el).toHaveClass('bb-tag--texture');
  });
});

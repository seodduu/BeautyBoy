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
});

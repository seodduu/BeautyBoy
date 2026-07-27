import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Tag } from './Tag';

describe('Tag', () => {
  it('kind별 폴백 클래스(effect/texture)가 slug 클래스와 함께 붙는다', () => {
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

  // jsdom은 실제 CSS 값(getComputedStyle)을 계산하지 않으므로(vitest css 처리 미적용),
  // 여기서는 "폴백 kind 클래스가 붙어 있다/slug 클래스가 그 뒤에 덮어쓴다"는 구조만 검증한다.
  // 실제로 폴백이 1px hairline 테두리 pill로, 팔레트 색이 테두리 없는 틴트 pill로 렌더되는지는
  // Tag.css의 규칙(kind 클래스 = border 1px hairline, slug 클래스 = border-color transparent)과
  // Task 3의 스크린샷 육안 확인으로 담보한다.
  it('폴백 kind 클래스(bb-tag--effect)는 팔레트 미정의 slug에서 유일한 스타일 담당 클래스다', () => {
    const { container } = render(<Tag view={{ name: '미정의', kind: 'EFFECT', slug: 'unknown-slug' }} />);
    const el = container.querySelector('.bb-tag')!;
    // unknown-slug 클래스는 Tag.css에 규칙이 없다 — bb-tag--effect가 유일하게 배경/테두리를 정의한다.
    expect(el.className.trim().split(/\s+/)).toEqual(['bb-tag', 'bb-tag--effect', 'bb-tag--unknown-slug']);
  });

  // TEXTURE는 더 이상 무채색이 아니다 — DESIGN.md "TEXTURE 3종 (사용감)" 표가 색을 배정했고
  // Tag.css의 slug 블록이 .bb-tag--texture 폴백보다 뒤에 있어 색이 이긴다.
  it('TEXTURE 3종은 slug 컬러 클래스를 받는다', () => {
    const { rerender, container } = render(<Tag view={{ name: '산뜻함', kind: 'TEXTURE', slug: 'fresh' }} />);
    expect(container.querySelector('.bb-tag')).toHaveClass('bb-tag--fresh');
    rerender(<Tag view={{ name: '촉촉함', kind: 'TEXTURE', slug: 'dewy' }} />);
    expect(container.querySelector('.bb-tag')).toHaveClass('bb-tag--dewy');
    rerender(<Tag view={{ name: '매트', kind: 'TEXTURE', slug: 'matte' }} />);
    expect(container.querySelector('.bb-tag')).toHaveClass('bb-tag--matte');
  });
});

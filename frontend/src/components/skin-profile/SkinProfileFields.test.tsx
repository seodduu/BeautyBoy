import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { SkinProfileFields, CONCERNS, TEXTURES } from './SkinProfileFields';
import type { Concern } from '../../api/auth';

function renderFields(overrides: Partial<Parameters<typeof SkinProfileFields>[0]> = {}) {
  const props = {
    skinType: undefined,
    concerns: [] as Concern[],
    ageBand: undefined,
    onChangeSkinType: vi.fn(),
    onToggleConcern: vi.fn(),
    onChangeAgeBand: vi.fn(),
    ...overrides,
  };
  return { ...render(<SkinProfileFields {...props} />), props };
}

/** legend 텍스트로 fieldset을 집는다 — group role의 접근성 이름이 곧 legend다. */
function groupByLegend(name: string | RegExp) {
  return screen.getByRole('group', { name });
}

describe('SkinProfileFields — 고민·사용감 슬러그', () => {
  it('고민 칩 9개와 사용감 칩 3개가 각각 별도 fieldset에 렌더된다', () => {
    renderFields();

    const concernGroup = groupByLegend(/고민/);
    const textureGroup = groupByLegend(/사용감/);
    expect(concernGroup).not.toBe(textureGroup);

    expect(within(concernGroup).getAllByRole('button')).toHaveLength(9);
    expect(within(textureGroup).getAllByRole('button')).toHaveLength(3);

    // 표시명은 tag 테이블의 name과 같아야 한다.
    for (const c of CONCERNS) {
      expect(within(concernGroup).getByRole('button', { name: c.label })).toBeInTheDocument();
    }
    for (const t of TEXTURES) {
      expect(within(textureGroup).getByRole('button', { name: t.label })).toBeInTheDocument();
    }
  });

  it("사용감 칩을 누르면 onToggleConcern이 'dewy'로 호출된다", () => {
    // 사용감은 별도 fieldset이지만 값은 같은 concerns 배열로 합류한다(설계 §4.1).
    const { props } = renderFields();

    fireEvent.click(screen.getByRole('button', { name: '촉촉함' }));

    expect(props.onToggleConcern).toHaveBeenCalledWith('dewy');
  });

  it('고민 칩을 누르면 같은 콜백이 슬러그로 호출된다', () => {
    const { props } = renderFields();

    fireEvent.click(screen.getByRole('button', { name: '모공' }));

    expect(props.onToggleConcern).toHaveBeenCalledWith('pore');
  });

  it('선택된 칩은 aria-pressed=true 이고 반전 클래스(bb-chip--on)를 함께 받는다', () => {
    renderFields({ concerns: ['pore', 'dewy'] });

    const pore = screen.getByRole('button', { name: '모공' });
    expect(pore).toHaveAttribute('aria-pressed', 'true');
    expect(pore).toHaveClass('bb-chip--on');
    expect(pore).toHaveClass('bb-chip--pore');

    const dewy = screen.getByRole('button', { name: '촉촉함' });
    expect(dewy).toHaveAttribute('aria-pressed', 'true');
    expect(dewy).toHaveClass('bb-chip--on');
    expect(dewy).toHaveClass('bb-chip--dewy');

    // 미선택 칩은 slug 클래스만 받고 반전 클래스는 받지 않는다.
    const trouble = screen.getByRole('button', { name: '트러블' });
    expect(trouble).toHaveAttribute('aria-pressed', 'false');
    expect(trouble).toHaveClass('bb-chip--trouble');
    expect(trouble).not.toHaveClass('bb-chip--on');
  });

  it('체크 표시는 aria-hidden이라 접근성 이름을 늘리지 않는다', () => {
    renderFields({ concerns: ['pore'] });

    // aria-pressed가 이미 상태를 알리므로 ✓는 스크린리더에서 감춘다.
    const pore = screen.getByRole('button', { name: '모공' });
    expect(pore.textContent).toContain('✓');
  });

  it('선택된 피부타입 카드는 bb-skin-type-card--active 와 slug 클래스를 함께 받는다', () => {
    const { container } = renderFields({ skinType: 'DRY' });

    const dry = container.querySelector('.bb-skin-type-card--dry')!;
    expect(dry).toHaveClass('bb-skin-type-card--active');

    // 미선택 카드도 slug 클래스는 받는다(좌측 컬러 바가 미선택 상태의 단서다).
    const oily = container.querySelector('.bb-skin-type-card--oily')!;
    expect(oily).not.toHaveClass('bb-skin-type-card--active');
  });

  it('피부타입 라디오의 접근성 이름은 라벨만, 설명은 description으로 남는다', () => {
    renderFields({ skinType: 'DRY' });

    // 기존 aria-labelledby/aria-describedby 설계가 색 반전 도입 후에도 유지돼야 한다.
    const dry = screen.getByRole('radio', { name: '건성' });
    expect(dry).toBeChecked();
    expect(dry).toHaveAttribute('aria-describedby');
  });
});

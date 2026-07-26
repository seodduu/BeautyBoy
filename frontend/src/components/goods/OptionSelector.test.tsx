import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { OptionSelector } from './OptionSelector';
import type { GoodsOption } from '../../types/detail';

const OPTIONS: GoodsOption[] = [
  { optionNo: 1, name: '200ml', addPrice: 0, stock: 50, soldOut: false },
  { optionNo: 2, name: '300ml', addPrice: 3000, stock: 80, soldOut: false },
  { optionNo: 3, name: '500ml', addPrice: 5000, stock: 0, soldOut: false },
];

describe('OptionSelector', () => {
  it('role=radiogroup에 옵션 이름을 나열하고, 처음에는 아무것도 선택되지 않는다', () => {
    render(<OptionSelector options={OPTIONS} selectedOptionNo={null} onSelect={vi.fn()} />);

    expect(screen.getByRole('radiogroup', { name: /옵션/ })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /200ml/ })).not.toBeChecked();
    expect(screen.getByRole('radio', { name: /300ml/ })).not.toBeChecked();
  });

  it('addPrice가 있으면 "(+3,000원)"을 이름 뒤에 붙인다', () => {
    render(<OptionSelector options={OPTIONS} selectedOptionNo={null} onSelect={vi.fn()} />);

    expect(screen.getByRole('radio', { name: /300ml \(\+3,000원\)/ })).toBeInTheDocument();
  });

  it('addPrice가 0이면 가격 접미사를 붙이지 않는다', () => {
    render(<OptionSelector options={OPTIONS} selectedOptionNo={null} onSelect={vi.fn()} />);

    const option = screen.getByRole('radio', { name: /^200ml$/ });
    expect(option).toBeInTheDocument();
  });

  it('stock이 0인 옵션은 "(품절)" 표시와 함께 선택할 수 없다', () => {
    render(<OptionSelector options={OPTIONS} selectedOptionNo={null} onSelect={vi.fn()} />);

    expect(screen.getByRole('radio', { name: /품절/ })).toBeDisabled();
  });

  it('selectedOptionNo와 일치하는 옵션만 checked 상태다', () => {
    render(<OptionSelector options={OPTIONS} selectedOptionNo={2} onSelect={vi.fn()} />);

    expect(screen.getByRole('radio', { name: /200ml/ })).not.toBeChecked();
    expect(screen.getByRole('radio', { name: /300ml/ })).toBeChecked();
  });

  it('옵션을 클릭하면 onSelect가 해당 optionNo로 호출된다', () => {
    const onSelect = vi.fn();
    render(<OptionSelector options={OPTIONS} selectedOptionNo={null} onSelect={onSelect} />);

    fireEvent.click(screen.getByRole('radio', { name: /300ml/ }));

    expect(onSelect).toHaveBeenCalledWith(2);
  });

  it('품절 옵션을 클릭해도 onSelect가 호출되지 않는다', () => {
    const onSelect = vi.fn();
    render(<OptionSelector options={OPTIONS} selectedOptionNo={null} onSelect={onSelect} />);

    fireEvent.click(screen.getByRole('radio', { name: /품절/ }));

    expect(onSelect).not.toHaveBeenCalled();
  });
});

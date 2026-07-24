import { expect, test, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AutocompleteOverlay, getOptionId, useAutocompleteNav } from './AutocompleteOverlay';

const LISTBOX_ID = 'ac-listbox';

/**
 * 실제 사용(SearchBox)을 흉내낸 최소 하네스 — 입력의 onKeyDown에 훅을 붙이고,
 * 하이라이트 인덱스를 오버레이에 그대로 내려 aria-activedescendant/옵션 id가 맞물리는지 본다.
 */
function Harness({
  suggestions,
  onSelect,
}: {
  suggestions: string[];
  onSelect: (value: string) => void;
}) {
  const { activeIndex, handleKeyDown } = useAutocompleteNav({ suggestions, onSelect });

  return (
    <div>
      <input
        aria-label="검색어"
        role="combobox"
        aria-expanded={suggestions.length > 0}
        aria-controls={LISTBOX_ID}
        aria-activedescendant={activeIndex >= 0 ? getOptionId(LISTBOX_ID, activeIndex) : undefined}
        onKeyDown={handleKeyDown}
      />
      <AutocompleteOverlay
        id={LISTBOX_ID}
        suggestions={suggestions}
        activeIndex={activeIndex}
        onSelect={onSelect}
      />
    </div>
  );
}

test('제안 목록은 role=option으로 렌더된다', () => {
  render(<Harness suggestions={['토너', '토닉']} onSelect={vi.fn()} />);

  const options = screen.getAllByRole('option');
  expect(options).toHaveLength(2);
  expect(options[0]).toHaveTextContent('토너');
  expect(options[1]).toHaveTextContent('토닉');
});

test('ArrowDown은 다음 옵션을 하이라이트하고 입력의 aria-activedescendant를 그 옵션 id로 맞춘다', () => {
  render(<Harness suggestions={['토너', '토닉']} onSelect={vi.fn()} />);

  const input = screen.getByRole('combobox');
  fireEvent.keyDown(input, { key: 'ArrowDown' });

  const expectedId = getOptionId(LISTBOX_ID, 0);
  expect(input).toHaveAttribute('aria-activedescendant', expectedId);

  const highlighted = screen.getByRole('option', { name: '토너' });
  expect(highlighted).toHaveAttribute('id', expectedId);
  expect(highlighted.className).toMatch(/bb-autocomplete__item--active/);
  expect(highlighted).toHaveAttribute('aria-selected', 'true');
});

test('ArrowDown 두 번이면 두 번째 옵션이 하이라이트된다', () => {
  render(<Harness suggestions={['토너', '토닉']} onSelect={vi.fn()} />);

  const input = screen.getByRole('combobox');
  fireEvent.keyDown(input, { key: 'ArrowDown' });
  fireEvent.keyDown(input, { key: 'ArrowDown' });

  expect(input).toHaveAttribute('aria-activedescendant', getOptionId(LISTBOX_ID, 1));
  expect(screen.getByRole('option', { name: '토닉' }).className).toMatch(
    /bb-autocomplete__item--active/,
  );
});

test('Enter는 하이라이트된 옵션 값으로 선택 콜백을 호출한다', () => {
  const onSelect = vi.fn();
  render(<Harness suggestions={['토너', '토닉']} onSelect={onSelect} />);

  const input = screen.getByRole('combobox');
  fireEvent.keyDown(input, { key: 'ArrowDown' });
  fireEvent.keyDown(input, { key: 'ArrowDown' });
  fireEvent.keyDown(input, { key: 'Enter' });

  expect(onSelect).toHaveBeenCalledWith('토닉');
});

test('마우스로 옵션을 클릭해도 선택 콜백이 호출된다', () => {
  const onSelect = vi.fn();
  render(<Harness suggestions={['토너', '토닉']} onSelect={onSelect} />);

  fireEvent.mouseDown(screen.getByRole('option', { name: '토너' }));

  expect(onSelect).toHaveBeenCalledWith('토너');
});

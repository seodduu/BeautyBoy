import { useEffect, useState, type KeyboardEvent } from 'react';
import './AutocompleteOverlay.css';

/** listbox id와 인덱스로 옵션 id를 만든다. 입력의 aria-activedescendant와 옵션 id가 같은 규칙을 써야 한다. */
export function getOptionId(listboxId: string, index: number): string {
  return `${listboxId}-option-${index}`;
}

interface UseAutocompleteNavParams {
  suggestions: string[];
  onSelect: (value: string) => void;
  onClose?: () => void;
}

/**
 * 자동완성 키보드 모델(ArrowDown/ArrowUp/Enter/Escape) — 실제 입력(SearchBox)의
 * onKeyDown에 붙이는 훅. 하이라이트 인덱스(activeIndex)만 갖고, 렌더는 AutocompleteOverlay가 맡는다.
 */
export function useAutocompleteNav({ suggestions, onSelect, onClose }: UseAutocompleteNavParams) {
  const [activeIndex, setActiveIndex] = useState(-1);

  // 후보 목록이 바뀌면(타이핑마다 갱신) 이전 하이라이트는 의미가 없어진다.
  useEffect(() => {
    setActiveIndex(-1);
  }, [suggestions]);

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (suggestions.length === 0) {
      return;
    }

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActiveIndex((current) => (current + 1) % suggestions.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActiveIndex((current) => (current - 1 + suggestions.length) % suggestions.length);
    } else if (event.key === 'Enter') {
      if (activeIndex >= 0) {
        event.preventDefault();
        onSelect(suggestions[activeIndex]);
      }
    } else if (event.key === 'Escape') {
      onClose?.();
    }
  }

  return { activeIndex, handleKeyDown, setActiveIndex };
}

interface AutocompleteOverlayProps {
  id: string;
  suggestions: string[];
  activeIndex: number;
  onSelect: (value: string) => void;
}

/**
 * 검색 입력 아래 뜨는 자동완성 목록. role="listbox" + role="option" 시맨틱을 갖추고,
 * 하이라이트된 옵션의 id는 입력의 aria-activedescendant가 가리키는 id(getOptionId)와 같다.
 */
export function AutocompleteOverlay({ id, suggestions, activeIndex, onSelect }: AutocompleteOverlayProps) {
  if (suggestions.length === 0) {
    return null;
  }

  return (
    <ul id={id} role="listbox" className="bb-autocomplete">
      {suggestions.map((suggestion, index) => (
        <li
          key={suggestion}
          id={getOptionId(id, index)}
          role="option"
          aria-selected={index === activeIndex}
          className={`bb-autocomplete__item${
            index === activeIndex ? ' bb-autocomplete__item--active' : ''
          }`}
          // mousedown에서 선택해야 blur로 오버레이가 먼저 닫히는 걸 막는다.
          onMouseDown={(event) => {
            event.preventDefault();
            onSelect(suggestion);
          }}
        >
          {suggestion}
        </li>
      ))}
    </ul>
  );
}

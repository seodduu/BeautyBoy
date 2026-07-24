import { useEffect, useId, useState } from 'react';
import { fetchAutocomplete } from '../../api/search';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import { AutocompleteOverlay, getOptionId, useAutocompleteNav } from './AutocompleteOverlay';
import './SearchBox.css';

/** 자동완성 호출 디바운스 간격(ms). 빠르게 타이핑하는 동안은 마지막 입력값만 호출된다. */
const AUTOCOMPLETE_DEBOUNCE_MS = 300;

interface SearchBoxProps {
  /** URL(?q=)에서 온 현재 검색어 — 딥링크·뒤로가기로 바뀌면 입력값도 맞춘다. */
  initialQuery: string;
  /** 제출(Enter/버튼) 또는 자동완성 선택 시 확정된 검색어를 올려보낸다. */
  onSearch: (keyword: string) => void;
  /** 자동완성 후보 개수가 바뀔 때마다 호출 — 페이지의 aria-live 안내에 쓴다. */
  onSuggestionsChange?: (count: number) => void;
}

/**
 * 검색 입력 + 자동완성 오버레이. URL 상태는 이 컴포넌트가 아니라 부모(Search 페이지)가 갖는다 —
 * 이 컴포넌트는 "확정된 검색어"만 onSearch로 올려보낸다.
 */
export function SearchBox({ initialQuery, onSearch, onSuggestionsChange }: SearchBoxProps) {
  const [draft, setDraft] = useState(initialQuery);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [open, setOpen] = useState(false);
  const listboxId = useId();

  // 딥링크(/search?q=토너)나 뒤로가기로 바깥에서 검색어가 바뀌면 입력값도 따라간다.
  useEffect(() => {
    setDraft(initialQuery);
  }, [initialQuery]);

  // 타이핑마다 새 타이머를 걸고 cleanup에서 이전 타이머를 지운다 — 300ms 안에 또 타이핑하면
  // 이전 예약은 취소되고 마지막 값만 실제로 fetchAutocomplete를 호출한다("마지막 호출만 승리").
  useEffect(() => {
    const keyword = draft.trim();
    if (!keyword) {
      setSuggestions([]);
      setOpen(false);
      return;
    }

    const timer = setTimeout(() => {
      fetchAutocomplete(keyword)
        .then((result) => {
          setSuggestions(result);
          setOpen(result.length > 0);
        })
        .catch(() => {
          setSuggestions([]);
          setOpen(false);
        });
    }, AUTOCOMPLETE_DEBOUNCE_MS);

    return () => clearTimeout(timer);
  }, [draft]);

  useEffect(() => {
    onSuggestionsChange?.(suggestions.length);
  }, [suggestions, onSuggestionsChange]);

  function commit(keyword: string) {
    const trimmed = keyword.trim();
    setDraft(trimmed);
    setSuggestions([]);
    setOpen(false);
    onSearch(trimmed);
  }

  const activeSuggestions = open ? suggestions : [];
  const { activeIndex, handleKeyDown } = useAutocompleteNav({
    suggestions: activeSuggestions,
    onSelect: commit,
    onClose: () => setOpen(false),
  });

  const isOverlayOpen = open && suggestions.length > 0;

  return (
    <form
      className="bb-search-box"
      role="search"
      onSubmit={(event) => {
        event.preventDefault();
        commit(draft);
      }}
    >
      <div className="bb-search-box__input-wrap">
        <Field
          id="search-keyword"
          label="검색어"
          value={draft}
          onChange={setDraft}
          onKeyDown={handleKeyDown}
          placeholder="스킨케어, 클렌징, 헤어 검색"
          autoComplete="off"
          role="combobox"
          aria-expanded={isOverlayOpen}
          aria-controls={listboxId}
          aria-activedescendant={
            isOverlayOpen && activeIndex >= 0 ? getOptionId(listboxId, activeIndex) : undefined
          }
        />
        {isOverlayOpen && (
          <AutocompleteOverlay
            id={listboxId}
            suggestions={suggestions}
            activeIndex={activeIndex}
            onSelect={commit}
          />
        )}
      </div>
      <Button type="submit">검색</Button>
    </form>
  );
}

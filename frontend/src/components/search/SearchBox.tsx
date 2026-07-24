import { useEffect, useId, useRef, useState } from 'react';
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
  // draft 변경이 "사용자가 지금 타이핑해서" 온 것인지 표시하는 플래그. 마운트 시 초기값 세팅,
  // 딥링크·뒤로가기로 인한 initialQuery 동기화, commit() 이후에는 false로 되돌려
  // 자동완성 오버레이가 사용자 타이핑 없이 열리지 않게 한다.
  const typingRef = useRef(false);

  // 딥링크(/search?q=토너)나 뒤로가기로 바깥에서 검색어가 바뀌면 입력값도 따라간다.
  // 이건 사용자가 타이핑한 게 아니므로 typingRef를 내려 자동완성이 뜨지 않게 한다.
  useEffect(() => {
    typingRef.current = false;
    setDraft(initialQuery);
  }, [initialQuery]);

  // 타이핑마다 새 타이머를 걸고 cleanup에서 이전 타이머를 지운다 — 300ms 안에 또 타이핑하면
  // 이전 예약은 취소되고 마지막 값만 실제로 fetchAutocomplete를 호출한다("마지막 호출만 승리").
  useEffect(() => {
    const keyword = draft.trim();
    if (!keyword || !typingRef.current) {
      setSuggestions([]);
      setOpen(false);
      return;
    }

    const timer = setTimeout(() => {
      // 타이머가 발화하는 시점에도 여전히 사용자 타이핑 유래인지 다시 확인한다 —
      // 그 사이 commit()이나 딥링크 동기화가 typingRef를 꺼뜨렸을 수 있다.
      if (!typingRef.current) {
        return;
      }
      fetchAutocomplete(keyword)
        .then((result) => {
          if (!typingRef.current) {
            return;
          }
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
    typingRef.current = false;
    setDraft(trimmed);
    setSuggestions([]);
    setOpen(false);
    onSearch(trimmed);
  }

  function handleDraftChange(value: string) {
    // Field의 onChange는 사용자 입력에서만 불린다 — 여기서만 typingRef를 켠다.
    typingRef.current = true;
    setDraft(value);
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
          onChange={handleDraftChange}
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

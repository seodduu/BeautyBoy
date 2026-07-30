import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { fetchPopularKeywords, fetchSearch } from '../api/search';
import { EmptyState } from '../components/common/EmptyState';
import { GoodsGrid } from '../components/goods/GoodsGrid';
import { SearchBox } from '../components/search/SearchBox';
import { useWishToggle } from '../features/wishlist/useWishToggle';
import { useTitle } from '../hooks/useTitle';
import './Search.css';

/**
 * 검색 페이지 `/search?q=`. URL이 상태다 — q가 바뀌면(딥링크·뒤로가기 포함) 그대로 다시 검색한다.
 * 무결과는 EmptyState + 인기 검색어(대체 제안)로 이월 소비처를 만든다(DESIGN.md 네비게이션·검색).
 */
export function Search() {
  const [searchParams, setSearchParams] = useSearchParams();
  const q = searchParams.get('q') ?? '';
  const hasQuery = q.length > 0;

  // 검색어는 URL 파라미터라 즉시 확정된다 — 로딩 대기 없이 바로 세운다(설계 §1.3).
  useTitle(hasQuery ? `'${q}' 검색 결과` : '검색');

  const toggleWish = useWishToggle();
  const [suggestionCount, setSuggestionCount] = useState(0);
  const [liveMessage, setLiveMessage] = useState('');

  const searchQuery = useQuery({
    queryKey: ['search', q],
    queryFn: () => fetchSearch(q),
    enabled: hasQuery,
  });

  const popularQuery = useQuery({
    queryKey: ['search-popular-keywords'],
    queryFn: fetchPopularKeywords,
  });

  // 자동완성 후보가 뜰 때마다 "제안 N건"을 안내한다. 타이핑 중간의 매 키 입력이 아니라
  // SearchBox가 디바운스를 거쳐 확정한 후보 개수만 넘어온다.
  useEffect(() => {
    if (suggestionCount > 0) {
      setLiveMessage(`제안 ${suggestionCount}건`);
    }
  }, [suggestionCount]);

  // 검색이 끝나면(성공) 결과 건수 또는 무결과를 안내한다.
  useEffect(() => {
    if (!hasQuery || !searchQuery.isSuccess || !searchQuery.data) {
      return;
    }
    if (searchQuery.data.content.length === 0) {
      setLiveMessage('검색 결과가 없습니다');
    } else {
      setLiveMessage(`검색 결과 ${searchQuery.data.totalElements}건`);
    }
  }, [hasQuery, searchQuery.isSuccess, searchQuery.data]);

  function runSearch(keyword: string) {
    const trimmed = keyword.trim();
    if (!trimmed) {
      return;
    }
    setSearchParams({ q: trimmed });
  }

  const popularKeywords = popularQuery.data ?? [];
  const isEmptyResult =
    hasQuery && searchQuery.isSuccess && (searchQuery.data?.content.length ?? 0) === 0;

  function renderPopularKeywords() {
    if (popularKeywords.length === 0) {
      return null;
    }
    return (
      <section className="bb-search__popular" aria-label="인기 검색어">
        <h2 className="bb-search__popular-title">인기 검색어</h2>
        <div className="bb-search__chips">
          {popularKeywords.map((keyword) => (
            <button
              key={keyword}
              type="button"
              className="bb-search__chip"
              onClick={() => runSearch(keyword)}
            >
              {keyword}
            </button>
          ))}
        </div>
      </section>
    );
  }

  return (
    <div className="bb-search">
      <h1 className="bb-search__title">검색</h1>

      <SearchBox initialQuery={q} onSearch={runSearch} onSuggestionsChange={setSuggestionCount} />

      {/* 이월 항목: 자동완성 제안 건수·검색 결과 건수를 알리는 전용 polite 라이브리전.
          토스트(전역, 일시적 성공/실패 알림)와는 별개다 — 매 키 입력마다 토스트를 띄우면
          스크린리더 사용자에게 소음이 되므로 여기서만 조용히 안내한다. */}
      <p className="bb-search__live-region" role="status" aria-live="polite">
        {liveMessage}
      </p>

      {hasQuery && searchQuery.isError && (
        <p className="bb-search__error">검색에 실패했어요. 잠시 후 다시 시도해 주세요.</p>
      )}

      {hasQuery && !searchQuery.isError && isEmptyResult && (
        <>
          <EmptyState
            title={`'${q}'에 대한 검색 결과가 없어요`}
            description="다른 검색어로 다시 찾아보세요"
          />
          {renderPopularKeywords()}
        </>
      )}

      {hasQuery && !searchQuery.isError && !isEmptyResult && (
        <GoodsGrid
          items={searchQuery.data?.content ?? []}
          loading={searchQuery.isLoading}
          onWishToggle={toggleWish}
        />
      )}

      {!hasQuery && renderPopularKeywords()}
    </div>
  );
}

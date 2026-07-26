import { api } from './client';
import type { ApiEnvelope, PageResponse } from '../types/goods';
import type { SearchResultItem } from '../types/search';

/** GET /search — 검색 결과. sort 기본값은 서버에서 "accuracy"로 처리한다. */
export interface FetchSearchParams {
  sort?: string;
  page?: number;
}

/** GET /search — 검색어 q와 정렬·페이지를 querystring으로 넘기고 ApiEnvelope를 벗겨 반환한다. */
export async function fetchSearch(
  q: string,
  params: FetchSearchParams = {},
): Promise<PageResponse<SearchResultItem>> {
  const response = await api.get<ApiEnvelope<PageResponse<SearchResultItem>>>('/search', {
    params: { q, ...params },
  });
  return response.data.data;
}

/** GET /search/autocomplete — 입력 중 자동완성 후보 목록. */
export async function fetchAutocomplete(q: string): Promise<string[]> {
  const response = await api.get<ApiEnvelope<string[]>>('/search/autocomplete', {
    params: { q },
  });
  return response.data.data;
}

/** GET /search/popular-keywords — 인기 검색어 목록. */
export async function fetchPopularKeywords(): Promise<string[]> {
  const response = await api.get<ApiEnvelope<string[]>>('/search/popular-keywords');
  return response.data.data;
}

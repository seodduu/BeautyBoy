import { api } from './client';
import type { ApiEnvelope } from '../types/goods';
import type { RankingItem } from '../types/ranking';

/** GET /rankings — 바로 List<RankingItem>을 반환하며 PageResponse로 감싸지 않는다. */
export async function fetchRanking(categoryCode?: string): Promise<RankingItem[]> {
  const response = await api.get<ApiEnvelope<RankingItem[]>>('/rankings', {
    params: categoryCode ? { categoryCode } : undefined,
  });
  return response.data.data;
}

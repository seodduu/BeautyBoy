import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { fetchSearch } from './search';
import { server } from '../mocks/server';
import type { ApiEnvelope, GoodsListItem, PageResponse } from '../types/goods';

const sampleItem: GoodsListItem = {
  goodsNo: 1,
  brandName: '이니스프리',
  name: '그린티 씨드 세럼',
  thumbnailUrl: 'https://example.com/thumb.jpg',
  listPrice: 30000,
  salePrice: 24000,
  discountRate: 20,
  badges: ['SALE'],
  rating: 4.5,
  reviewCount: 12,
  wished: false,
  todayDreamAvailable: true,
};

describe('fetchSearch — msw 목 서버 대상', () => {
  it('검색 결과를 PageResponse<GoodsListItem>로 언랩한다', async () => {
    server.use(
      http.get('/api/v1/search', () => {
        const body: ApiEnvelope<PageResponse<GoodsListItem>> = {
          code: 'OK',
          message: 'success',
          data: {
            content: [sampleItem],
            page: 0,
            size: 20,
            totalElements: 1,
            totalPages: 1,
            hasNext: false,
          },
        };
        return HttpResponse.json(body);
      }),
    );

    const result = await fetchSearch('토너');

    expect(result.content).toHaveLength(1);
    expect(result.content[0].goodsNo).toBe(1);
    expect(result.totalElements).toBe(1);
  });

  it('q, sort, page가 쿼리스트링으로 직렬화된다', async () => {
    let capturedUrl: URL | undefined;

    server.use(
      http.get('/api/v1/search', ({ request }) => {
        capturedUrl = new URL(request.url);
        const body: ApiEnvelope<PageResponse<GoodsListItem>> = {
          code: 'OK',
          message: 'success',
          data: { content: [], page: 1, size: 20, totalElements: 0, totalPages: 0, hasNext: false },
        };
        return HttpResponse.json(body);
      }),
    );

    await fetchSearch('토너', { sort: 'priceAsc', page: 1 });

    expect(capturedUrl?.searchParams.get('q')).toBe('토너');
    expect(capturedUrl?.searchParams.get('sort')).toBe('priceAsc');
    expect(capturedUrl?.searchParams.get('page')).toBe('1');
  });
});

import { api } from './client';
import type { ApiEnvelope, PageResponse } from '../types/goods';
import type { QnaItem } from '../types/review';

/** GET /qna — 읽기 전용 문의 목록. */
export async function fetchQna(
  goodsNo: number,
  params: { page?: number } = {},
): Promise<PageResponse<QnaItem>> {
  const response = await api.get<ApiEnvelope<PageResponse<QnaItem>>>('/qna', {
    params: { goodsNo, ...params },
  });
  return response.data.data;
}

/** POST /qna 요청 바디 — backend QnaCreateRequest와 필드를 1:1로 맞춘다. */
export interface QnaCreateInput {
  goodsNo: number;
  question: string;
  isSecret: boolean;
}

/** POST /qna — 문의 등록. 인증 필요(SecurityConfig anyRequest().authenticated()). */
export async function createQna(input: QnaCreateInput): Promise<void> {
  await api.post('/qna', input);
}

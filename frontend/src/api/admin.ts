import { api } from './client';
import type { ApiEnvelope, PageResponse } from '../types/goods';

/**
 * GET /admin/goods 응답 아이템.
 * 실제 백엔드 AdminGoodsListItem(backend/src/main/java/com/beautyboy/catalog/dto/AdminGoodsListItem.java)과
 * 필드를 맞춘다.
 *
 * Task 4-14a가 AdminGoodsService.list()에 status를 얹어 내려주기 시작했다(GoodsQueryRepository의
 * AdminGoodsRow가 status 컬럼을 프로젝션한다) — 이제 실 서버 응답에 항상 존재하므로 optional이 아니다.
 * Goods.java의 실제 상태 3값(ON_SALE/SOLD_OUT/HIDDEN)을 그대로 반영한다.
 */
export interface AdminGoodsListItem {
  goodsNo: number;
  brandName: string;
  name: string;
  thumbnailUrl: string;
  listPrice: number;
  salePrice: number;
  discountRate: number;
  badges: string[];
  rating: number;
  reviewCount: number;
  wished: boolean;
  todayDreamAvailable: boolean;
  status: 'ON_SALE' | 'SOLD_OUT' | 'HIDDEN';
}

/**
 * GET /admin/goods/{goodsNo} 응답 — 인라인 수정 폼 채움 전용(Task 4-14a).
 * 실제 백엔드 AdminGoodsDetailResponse(backend/.../catalog/dto/AdminGoodsDetailResponse.java)와
 * 필드를 1:1로 맞춘다. 공개 상세(GoodsDetail, api/goods.ts)와 달리 **HIDDEN 상품도 조회된다**
 * (AdminGoodsService.detail()이 findById를 쓴다 — GoodsService.detail()의 STATUS_HIDDEN 제외와 다른 지점).
 */
export interface AdminGoodsDetailResponse {
  goodsNo: number;
  brandId: number;
  categoryCode: string;
  name: string;
  summary: string;
  thumbnailUrl: string;
  listPrice: number;
  salePrice: number;
  status: string;
}

/**
 * GET /admin/goods/{goodsNo} — 인라인 수정 진입 시 실제 categoryCode·summary·brandId·status를
 * 확보하기 위해 부른다. HIDDEN 상품도 조회되므로(위 AdminGoodsDetailResponse 문서 참고)
 * 숨김 상품도 인라인 수정에 진입할 수 있다.
 */
export async function fetchAdminGoodsDetail(goodsNo: number): Promise<AdminGoodsDetailResponse> {
  const response = await api.get<ApiEnvelope<AdminGoodsDetailResponse>>(`/admin/goods/${goodsNo}`);
  return response.data.data;
}

/** POST/PUT /admin/goods 요청 바디 — backend AdminGoodsSaveRequest와 필드를 1:1로 맞춘다. */
export interface AdminGoodsSaveInput {
  brandId: number;
  categoryCode: string;
  name: string;
  summary: string;
  thumbnailUrl: string;
  listPrice: number;
  salePrice: number;
  status: string;
}

/** GET /admin/goods */
export async function fetchAdminGoods(
  params: { q?: string; page?: number; size?: number } = {},
): Promise<PageResponse<AdminGoodsListItem>> {
  const response = await api.get<ApiEnvelope<PageResponse<AdminGoodsListItem>>>('/admin/goods', { params });
  return response.data.data;
}

/** POST /admin/goods — 생성된 goodsNo를 반환한다(AdminGoodsController.create). */
export async function createAdminGoods(input: AdminGoodsSaveInput): Promise<number> {
  const response = await api.post<ApiEnvelope<number>>('/admin/goods', input);
  return response.data.data;
}

/** PUT /admin/goods/{goodsNo} */
export async function updateAdminGoods(goodsNo: number, input: AdminGoodsSaveInput): Promise<void> {
  await api.put(`/admin/goods/${goodsNo}`, input);
}

/**
 * DELETE /admin/goods/{goodsNo} — 물리 삭제가 아니라 status를 HIDDEN으로 내린다
 * (AdminGoodsService.delete Javadoc: 주문·리뷰·찜·루틴이 goods_no를 논리 참조하기 때문).
 */
export async function deleteAdminGoods(goodsNo: number): Promise<void> {
  await api.delete(`/admin/goods/${goodsNo}`);
}

/** GET /admin/routines 응답의 단계 — backend AdminRoutineStepResponse와 필드를 맞춘다. */
export interface AdminRoutineStep {
  stepOrder: number;
  stepName: string;
  beginnerTip: string;
  goodsNos: number[];
}

/** GET /admin/routines 응답의 템플릿 — backend AdminRoutineTemplateResponse와 필드를 맞춘다. */
export interface AdminRoutineTemplate {
  templateId: number;
  name: string;
  skinType: string;
  timeSlot: string;
  description: string;
  steps: AdminRoutineStep[];
}

/** GET /admin/routines */
export async function fetchAdminRoutines(): Promise<AdminRoutineTemplate[]> {
  const response = await api.get<ApiEnvelope<AdminRoutineTemplate[]>>('/admin/routines');
  return response.data.data;
}

/**
 * PUT /admin/routines/{templateId}/steps/{stepOrder}/goods — 이 단계의 추천 상품 목록을
 * "추가"가 아니라 goodsNos로 통째로 교체한다(backend RoutineStepGoodsRequest Javadoc).
 */
export async function replaceAdminRoutineStepGoods(
  templateId: number,
  stepOrder: number,
  goodsNos: number[],
): Promise<void> {
  await api.put(`/admin/routines/${templateId}/steps/${stepOrder}/goods`, { goodsNos });
}

/**
 * GET /admin/qna 응답 아이템 — 실제 백엔드 AdminQnaResponse(backend/.../qna/dto/AdminQnaResponse.java)와
 * 필드를 1:1로 맞춘다. 공개 QnaResponse와의 차이 둘: goodsNo를 포함하고(상품 필터 없이 전체를
 * 훑으므로 어느 상품인지 알아야 한다), question이 마스킹되지 않는다(admin에게는 비밀글도 본문
 * 그대로 내려온다 — QnaService.visibleQuestion의 admin 예외).
 */
export interface AdminQnaResponse {
  qnaId: number;
  goodsNo: number;
  question: string;
  isSecret: boolean;
  status: string;
  createdAt: string;
}

/**
 * GET /admin/qna — 상품 필터 없이 전체 문의를 조회한다(AdminQnaController.list,
 * qnaService.adminList가 미답변 우선으로 정렬한다). Task 4-14a 신설 엔드포인트 — 이전에는
 * 이 조회가 없어 admin이 공개 목록을 goodsNo로 손검색해 재사용했고, 그 경로는 비밀글 본문을
 * "비밀글입니다."로 마스킹해 admin이 내용을 볼 수 없었다(Task 4-14 KNOWN GAP, 4-14b에서 해소).
 */
export async function fetchAdminQna(
  params: { page?: number } = {},
): Promise<PageResponse<AdminQnaResponse>> {
  const response = await api.get<ApiEnvelope<PageResponse<AdminQnaResponse>>>('/admin/qna', { params });
  return response.data.data;
}

/** POST /admin/qna/{qnaId}/answer — 이미 답변된 문의면 서버가 QNA_ALREADY_ANSWERED로 거절한다. */
export async function answerAdminQna(qnaId: number, answer: string): Promise<void> {
  await api.post(`/admin/qna/${qnaId}/answer`, { answer });
}

import { api } from './client';
import type { ApiEnvelope, PageResponse } from '../types/goods';

/**
 * GET /admin/goods 응답 아이템.
 * 실제 백엔드 GoodsListItem(backend/src/main/java/com/beautyboy/catalog/dto/GoodsListItem.java:9-22)과
 * 필드를 맞춘다.
 *
 * KNOWN GAP(Task 4-14): 실제 GoodsListItem에는 `status`가 없다 — AdminGoodsService.list()가 쓰는
 * GoodsQueryRepository.findAdminList()도 status 컬럼을 프로젝션하지 않는다
 * (backend/.../catalog/GoodsQueryRepository.java:122-147, GoodsRow record는 goodsId/brandName/name/
 * thumbnailUrl/listPrice/salePrice 6개뿐). 즉 **실 서버 응답에서 이 필드는 항상 undefined다** —
 * 숨김 상품 배지("숨김")는 지금 백엔드로는 정확히 구현할 수 없다. catalog 패키지는 이 태스크의
 * Files 목록 밖이라 손대지 않았고, 백엔드 확장(예: GoodsListItem에 status append)이 필요하다는
 * 사실을 보고서로 넘긴다. mocks/handlers.ts는 화면 확인을 위해 이 필드를 채워 둔다.
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
  /** KNOWN GAP — 위 문서 주석 참고. 실 서버 응답에는 없다. */
  status?: 'ON_SALE' | 'HIDDEN';
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
 * POST /admin/qna/{qnaId}/answer — 이미 답변된 문의면 서버가 QNA_ALREADY_ANSWERED로 거절한다.
 *
 * KNOWN GAP(Task 4-14): admin이 문의 목록을 한 번에 조회하는 전용 엔드포인트가 없다
 * (backend/.../qna/AdminQnaController.java에는 answer 하나뿐). 이 화면은 실제로 존재하는
 * 공개 목록 GET /qna(goodsNo 필수)를 상품번호로 검색해 재사용한다. 또한 QnaService.visibleQuestion
 * (backend/.../qna/QnaService.java)의 주석 그대로 "관리자 노출은 Wave 4에서 role 검사로
 * 확장한다"고 적혀 있지만 실제로는 확장되지 않아 — admin이 봐도 비밀글은 작성자 본인이 아니면
 * "비밀글입니다."로 마스킹된 채 내려온다. 이 태스크는 qna 패키지가 Files 목록 밖이라 손대지
 * 않았고, 보고서로 넘긴다.
 */
export async function answerAdminQna(qnaId: number, answer: string): Promise<void> {
  await api.post(`/admin/qna/${qnaId}/answer`, { answer });
}

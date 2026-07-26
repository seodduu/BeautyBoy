import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createAdminGoods,
  deleteAdminGoods,
  fetchAdminGoods,
  fetchAdminGoodsDetail,
  updateAdminGoods,
  type AdminGoodsListItem,
  type AdminGoodsSaveInput,
} from '../../api/admin';
import { Button } from '../../components/ui/Button';
import { Field } from '../../components/ui/Field';
import { Skeleton } from '../../components/ui/Skeleton';
import { useToast } from '../../components/ui/useToast';
import './AdminGoods.css';

const EMPTY_DRAFT: AdminGoodsSaveInput = {
  brandId: 0,
  categoryCode: '',
  name: '',
  summary: '',
  thumbnailUrl: '',
  listPrice: 0,
  salePrice: 0,
  status: 'ON_SALE',
};

/**
 * 관리자 상품 관리 `/admin/goods` — 테이블 + 인라인 편집의 최소 형태(DESIGN.md 편집디자인 톤).
 *
 * 실 백엔드 AdminGoodsController(backend/.../catalog/AdminGoodsController.java)의 5개
 * 엔드포인트(GET 목록/GET 상세/POST/PUT/DELETE)를 그대로 매핑한다. 목록은 HIDDEN 상품도
 * 포함한다(AdminGoodsService.list Javadoc: "숨긴 상품을 관리자가 못 보면 되살릴 방법이 없다").
 *
 * `status`는 Task 4-14a부터 AdminGoodsListItem(admin 전용 목록 DTO)에 실려 내려온다 —
 * "숨김" 배지는 실 서버로도 정확히 동작한다.
 *
 * **인라인 수정과 데이터 손상 방지**: 백엔드 `Goods.updateInfo()`(catalog/Goods.java:113-123)는
 * 부분 수정 개념이 없다 — PUT이 name/summary/categoryCode/thumbnailUrl/listPrice/salePrice/status
 * 전부를 통째로 덮어쓴다. `AdminGoodsListItem`(목록 응답)에는 애초에 categoryCode·summary가 없어서
 * 그 두 값을 채우지 않은 채 PUT을 보내면 실제 카테고리·설명이 조용히 손상된다(빈 값 또는
 * 하드코딩된 폴백으로 덮어써짐 — 리뷰에서 잡힌 실제 버그, 4-14 fix report 참고). 그래서
 * `startEdit`은 수정 모드 진입 시 `GET /admin/goods/:goodsNo`(fetchAdminGoodsDetail,
 * categoryCode·summary·brandId·status를 전부 포함하는 admin 전용 상세 응답)를 먼저 불러 그
 * 값으로 폼을 채운 뒤에만 수정 모드를 연다 — 그러면 사용자가 안 건드린 필드도 원래 값 그대로
 * 다시 저장되어 덮어쓰기가 무해해진다.
 *
 * **HIDDEN 상품도 인라인 수정된다(Task 4-14b)**: 공개 상세(`GET /goods/:goodsNo`)는 HIDDEN을
 * 조회 대상에서 제외하지만, admin 전용 상세는 `findById`를 써서 HIDDEN도 조회한다
 * (AdminGoodsService.detail Javadoc 참고) — 그래서 숨김 상품도 실제 categoryCode·summary를
 * 확보해 안전하게 수정 모드에 들어갈 수 있다. 조회 자체가 실패(네트워크 오류 등)하면 그때만
 * 여전히 수정 모드 진입을 막는다(토스트 안내) — 손상 위험을 감수하고 진입시키지 않는다.
 */
export function AdminGoods() {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const listQuery = useQuery({ queryKey: ['admin-goods'], queryFn: () => fetchAdminGoods() });

  const [creating, setCreating] = useState(false);
  const [createDraft, setCreateDraft] = useState<AdminGoodsSaveInput>(EMPTY_DRAFT);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState<AdminGoodsSaveInput>(EMPTY_DRAFT);
  const [editLoadingId, setEditLoadingId] = useState<number | null>(null);
  const [confirmingId, setConfirmingId] = useState<number | null>(null);

  const invalidateList = () => queryClient.invalidateQueries({ queryKey: ['admin-goods'] });

  const createMutation = useMutation({
    mutationFn: (input: AdminGoodsSaveInput) => createAdminGoods(input),
    onSuccess: () => {
      invalidateList();
      toast('상품을 등록했어요');
      setCreateDraft(EMPTY_DRAFT);
      setCreating(false);
    },
  });

  const updateMutation = useMutation({
    mutationFn: (vars: { goodsNo: number; input: AdminGoodsSaveInput }) =>
      updateAdminGoods(vars.goodsNo, vars.input),
    onSuccess: () => {
      invalidateList();
      toast('상품을 수정했어요');
      setEditingId(null);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (goodsNo: number) => deleteAdminGoods(goodsNo),
    onSuccess: () => {
      invalidateList();
      toast('상품을 숨겼어요');
      setConfirmingId(null);
    },
  });

  async function startEdit(item: AdminGoodsListItem) {
    setConfirmingId(null);
    setEditLoadingId(item.goodsNo);
    try {
      // 실제 categoryCode·summary·brandId·status를 확보해야만 PUT(전체 덮어쓰기)이 무해하다 —
      // 위 컴포넌트 문서 주석 "인라인 수정과 데이터 손상 방지" 참고. admin 전용 상세는 HIDDEN도
      // 조회하므로 숨김 상품도 여기서 실제 값을 확보할 수 있다.
      const detail = await fetchAdminGoodsDetail(item.goodsNo);
      setEditDraft({
        brandId: detail.brandId,
        categoryCode: detail.categoryCode,
        name: detail.name,
        summary: detail.summary,
        thumbnailUrl: detail.thumbnailUrl,
        listPrice: detail.listPrice,
        salePrice: detail.salePrice,
        status: detail.status,
      });
      setEditingId(item.goodsNo);
    } catch {
      // 조회 자체가 실패한 경우(네트워크 오류 등)에만 진입을 막는다 — 실제 값을 못 가져온 채로는
      // categoryCode·summary가 손상될 수 있다.
      toast('상품 정보를 불러오지 못해 수정을 열 수 없어요.');
    } finally {
      setEditLoadingId(null);
    }
  }

  if (listQuery.isLoading) {
    return (
      <div className="bb-admin-goods">
        <Skeleton ratio="16 / 5" />
      </div>
    );
  }

  if (listQuery.isError) {
    return (
      <div className="bb-admin-goods">
        <p className="bb-admin-goods__error">상품 목록을 불러오지 못했어요.</p>
      </div>
    );
  }

  const items = listQuery.data?.content ?? [];

  return (
    <div className="bb-admin-goods">
      <div className="bb-admin-goods__header">
        <h2 className="bb-admin-goods__title">상품 관리</h2>
        <Button
          variant="ghost"
          onClick={() => {
            setEditingId(null);
            setCreating((prev) => !prev);
          }}
        >
          {creating ? '취소' : '새 상품 등록'}
        </Button>
      </div>

      {creating && (
        <div className="bb-admin-goods__form">
          <Field
            id="admin-goods-new-name"
            label="상품명"
            value={createDraft.name}
            onChange={(value) => setCreateDraft((prev) => ({ ...prev, name: value }))}
            required
          />
          <Field
            id="admin-goods-new-category"
            label="카테고리 코드"
            value={createDraft.categoryCode}
            onChange={(value) => setCreateDraft((prev) => ({ ...prev, categoryCode: value }))}
            required
          />
          <Field
            id="admin-goods-new-thumbnail"
            label="썸네일 URL"
            value={createDraft.thumbnailUrl}
            onChange={(value) => setCreateDraft((prev) => ({ ...prev, thumbnailUrl: value }))}
          />
          <Field
            id="admin-goods-new-list-price"
            label="정가"
            type="number"
            inputMode="numeric"
            value={String(createDraft.listPrice)}
            onChange={(value) => setCreateDraft((prev) => ({ ...prev, listPrice: Number(value) || 0 }))}
          />
          <Field
            id="admin-goods-new-sale-price"
            label="판매가"
            type="number"
            inputMode="numeric"
            value={String(createDraft.salePrice)}
            onChange={(value) => setCreateDraft((prev) => ({ ...prev, salePrice: Number(value) || 0 }))}
          />
          <Field
            id="admin-goods-new-brand-id"
            label="브랜드 ID"
            type="number"
            inputMode="numeric"
            value={String(createDraft.brandId)}
            onChange={(value) => setCreateDraft((prev) => ({ ...prev, brandId: Number(value) || 0 }))}
          />
          <Button
            variant="primary"
            onClick={() => createMutation.mutate(createDraft)}
            disabled={createMutation.isPending}
            loading={createMutation.isPending}
          >
            등록
          </Button>
        </div>
      )}

      <div className="bb-admin-goods__table-wrap">
        <table className="bb-admin-goods__table">
          <thead>
            <tr>
              <th scope="col">상품명</th>
              <th scope="col">브랜드</th>
              <th scope="col">가격</th>
              <th scope="col">상태</th>
              <th scope="col">액션</th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) =>
              editingId === item.goodsNo ? (
                <tr key={item.goodsNo} className="bb-admin-goods__row bb-admin-goods__row--editing">
                  <td colSpan={5}>
                    <div className="bb-admin-goods__edit-form">
                      <Field
                        id={`admin-goods-edit-name-${item.goodsNo}`}
                        label="상품명"
                        value={editDraft.name}
                        onChange={(value) => setEditDraft((prev) => ({ ...prev, name: value }))}
                        required
                      />
                      <Field
                        id={`admin-goods-edit-list-price-${item.goodsNo}`}
                        label="정가"
                        type="number"
                        inputMode="numeric"
                        value={String(editDraft.listPrice)}
                        onChange={(value) =>
                          setEditDraft((prev) => ({ ...prev, listPrice: Number(value) || 0 }))
                        }
                      />
                      <Field
                        id={`admin-goods-edit-sale-price-${item.goodsNo}`}
                        label="판매가"
                        type="number"
                        inputMode="numeric"
                        value={String(editDraft.salePrice)}
                        onChange={(value) =>
                          setEditDraft((prev) => ({ ...prev, salePrice: Number(value) || 0 }))
                        }
                      />
                      <div className="bb-admin-goods__edit-actions">
                        <Button variant="ghost" onClick={() => setEditingId(null)}>
                          취소
                        </Button>
                        <Button
                          variant="primary"
                          onClick={() =>
                            // editDraft는 startEdit에서 fetchAdminGoodsDetail로 채운 실제 값 위에
                            // 사용자가 고친 필드만 얹은 것이다 — 하드코딩 폴백 없이 그대로 보낸다.
                            updateMutation.mutate({ goodsNo: item.goodsNo, input: editDraft })
                          }
                          disabled={updateMutation.isPending}
                          loading={updateMutation.isPending}
                        >
                          저장
                        </Button>
                      </div>
                    </div>
                  </td>
                </tr>
              ) : (
                <tr key={item.goodsNo} className="bb-admin-goods__row">
                  <td>{item.name}</td>
                  <td>{item.brandName}</td>
                  <td>{item.salePrice.toLocaleString('ko-KR')}원</td>
                  <td>
                    {item.status === 'HIDDEN' && <span className="bb-admin-goods__hidden-badge">숨김</span>}
                    {item.status === 'SOLD_OUT' && <span className="bb-admin-goods__soldout-badge">품절</span>}
                  </td>
                  <td>
                    {confirmingId === item.goodsNo ? (
                      <span className="bb-admin-goods__confirm">
                        정말 삭제할까요?
                        <button
                          type="button"
                          className="bb-admin-goods__action"
                          onClick={() => setConfirmingId(null)}
                        >
                          취소
                        </button>
                        <button
                          type="button"
                          className="bb-admin-goods__action"
                          onClick={() => deleteMutation.mutate(item.goodsNo)}
                        >
                          확인
                        </button>
                      </span>
                    ) : (
                      <>
                        <button
                          type="button"
                          className="bb-admin-goods__action"
                          onClick={() => startEdit(item)}
                          disabled={editLoadingId === item.goodsNo}
                        >
                          {editLoadingId === item.goodsNo ? '불러오는 중…' : '수정'}
                        </button>
                        <button
                          type="button"
                          className="bb-admin-goods__action"
                          onClick={() => setConfirmingId(item.goodsNo)}
                        >
                          삭제
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ),
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

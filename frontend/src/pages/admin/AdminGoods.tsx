import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createAdminGoods,
  deleteAdminGoods,
  fetchAdminGoods,
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

function toEditInput(item: AdminGoodsListItem): AdminGoodsSaveInput {
  return {
    brandId: 0,
    categoryCode: '',
    name: item.name,
    summary: '',
    thumbnailUrl: item.thumbnailUrl,
    listPrice: item.listPrice,
    salePrice: item.salePrice,
    status: item.status ?? 'ON_SALE',
  };
}

/**
 * 관리자 상품 관리 `/admin/goods` — 테이블 + 인라인 편집의 최소 형태(DESIGN.md 편집디자인 톤).
 *
 * 실 백엔드 AdminGoodsController(backend/.../catalog/AdminGoodsController.java)의 4개
 * 엔드포인트(GET/POST/PUT/DELETE)를 그대로 매핑한다. 목록은 HIDDEN 상품도 포함한다
 * (AdminGoodsService.list Javadoc: "숨긴 상품을 관리자가 못 보면 되살릴 방법이 없다").
 *
 * KNOWN GAP: `status`는 실제 GoodsListItem에 없는 필드다(api/admin.ts의 AdminGoodsListItem
 * 문서 주석 참고) — 실 서버에서는 "숨김" 배지가 항상 비어 보인다. 백엔드 확장 전까지는
 * mock으로만 확인 가능하다.
 */
export function AdminGoods() {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const listQuery = useQuery({ queryKey: ['admin-goods'], queryFn: () => fetchAdminGoods() });

  const [creating, setCreating] = useState(false);
  const [createDraft, setCreateDraft] = useState<AdminGoodsSaveInput>(EMPTY_DRAFT);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState<AdminGoodsSaveInput>(EMPTY_DRAFT);
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

  function startEdit(item: AdminGoodsListItem) {
    setConfirmingId(null);
    setEditingId(item.goodsNo);
    setEditDraft(toEditInput(item));
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
                            updateMutation.mutate({
                              goodsNo: item.goodsNo,
                              input: { ...editDraft, categoryCode: editDraft.categoryCode || 'C001001' },
                            })
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
                        >
                          수정
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

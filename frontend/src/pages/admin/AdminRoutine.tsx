import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchAdminRoutines,
  replaceAdminRoutineStepGoods,
  type AdminRoutineStep,
  type AdminRoutineTemplate,
} from '../../api/admin';
import { ErrorState } from '../../components/common/ErrorState';
import { Button } from '../../components/ui/Button';
import { Skeleton } from '../../components/ui/Skeleton';
import { useToast } from '../../components/ui/useToast';
import { useTitle } from '../../hooks/useTitle';
import './AdminRoutine.css';

/** "101, 102, 103" 같은 콤마 구분 문자열을 goodsNo 배열로 파싱한다. 빈 토큰·NaN은 버린다. */
function parseGoodsNos(raw: string): number[] {
  return raw
    .split(',')
    .map((token) => token.trim())
    .filter((token) => token.length > 0)
    .map((token) => Number(token))
    .filter((n) => Number.isFinite(n));
}

function StepRow({ templateId, step }: { templateId: number; step: AdminRoutineStep }) {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(step.goodsNos.join(', '));

  const mutation = useMutation({
    mutationFn: (goodsNos: number[]) => replaceAdminRoutineStepGoods(templateId, step.stepOrder, goodsNos),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-routines'] });
      toast('추천 상품을 저장했어요');
      setEditing(false);
    },
  });

  return (
    <tr className="bb-admin-routine__row">
      <td>{step.stepOrder}</td>
      <td>{step.stepName}</td>
      <td>
        {editing ? (
          <input
            className="bb-admin-routine__goods-input"
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            aria-label={`${step.stepName} 추천 상품 goodsNo 목록`}
          />
        ) : (
          step.goodsNos.join(', ') || '없음'
        )}
      </td>
      <td>
        {editing ? (
          <div className="bb-admin-routine__actions">
            <button
              type="button"
              className="bb-admin-routine__action"
              onClick={() => {
                setDraft(step.goodsNos.join(', '));
                setEditing(false);
              }}
            >
              취소
            </button>
            <Button
              variant="primary"
              onClick={() => mutation.mutate(parseGoodsNos(draft))}
              disabled={mutation.isPending}
              loading={mutation.isPending}
            >
              저장
            </Button>
          </div>
        ) : (
          <button type="button" className="bb-admin-routine__action" onClick={() => setEditing(true)}>
            수정
          </button>
        )}
      </td>
    </tr>
  );
}

function TemplateSection({ template }: { template: AdminRoutineTemplate }) {
  return (
    <section className="bb-admin-routine__template">
      <h3 className="bb-admin-routine__template-title">
        {template.name} · {template.skinType} · {template.timeSlot}
      </h3>
      <div className="bb-admin-routine__table-wrap">
        <table className="bb-admin-routine__table">
          <thead>
            <tr>
              <th scope="col">단계</th>
              <th scope="col">단계명</th>
              <th scope="col">추천 상품(goodsNo)</th>
              <th scope="col">액션</th>
            </tr>
          </thead>
          <tbody>
            {template.steps.map((step) => (
              <StepRow key={step.stepOrder} templateId={template.templateId} step={step} />
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

/**
 * 관리자 루틴 관리 `/admin/routine` — AdminRoutineController(GET /admin/routines,
 * PUT /admin/routines/{templateId}/steps/{stepOrder}/goods)를 그대로 매핑한다.
 * 단계별 추천 상품은 "추가"가 아니라 goodsNo 목록 전체 교체다(RoutineStepGoodsRequest Javadoc) —
 * 그래서 편집 UI도 콤마 구분 목록을 통째로 다시 저장하는 형태다.
 */
export function AdminRoutine() {
  useTitle('루틴 관리');
  const query = useQuery({ queryKey: ['admin-routines'], queryFn: fetchAdminRoutines });

  if (query.isLoading) {
    return (
      <div className="bb-admin-routine">
        <Skeleton ratio="16 / 5" />
      </div>
    );
  }

  if (query.isError) {
    return (
      <div className="bb-admin-routine">
        <ErrorState title="루틴 템플릿을 불러오지 못했어요" onRetry={() => query.refetch()} />
      </div>
    );
  }

  const templates = query.data ?? [];

  return (
    <div className="bb-admin-routine">
      <h2 className="bb-admin-routine__title">루틴 관리</h2>
      {templates.length === 0 ? (
        <p className="bb-admin-routine__empty">등록된 루틴 템플릿이 없어요.</p>
      ) : (
        templates.map((template) => <TemplateSection key={template.templateId} template={template} />)
      )}
    </div>
  );
}

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { answerAdminQna, fetchAdminQna, type AdminQnaResponse } from '../../api/admin';
import { Button } from '../../components/ui/Button';
import { Skeleton } from '../../components/ui/Skeleton';
import { useToast } from '../../components/ui/useToast';
import './AdminQna.css';

/** 실제 백엔드 값은 WAITING이다(backend/.../qna/Qna.java:45,62) — QnaList.tsx와 동일 라벨. */
const STATUS_LABEL: Record<string, string> = {
  ANSWERED: '답변완료',
  WAITING: '답변대기',
};

function AnswerCell({ item }: { item: AdminQnaResponse }) {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [answering, setAnswering] = useState(false);
  const [answer, setAnswer] = useState('');

  const mutation = useMutation({
    mutationFn: () => answerAdminQna(item.qnaId, answer),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-qna'] });
      toast('답변을 등록했어요');
      setAnswering(false);
      setAnswer('');
    },
  });

  if (item.status === 'ANSWERED') {
    return <span className="bb-admin-qna__done">답변완료</span>;
  }

  if (!answering) {
    return (
      <button type="button" className="bb-admin-qna__action" onClick={() => setAnswering(true)}>
        답변
      </button>
    );
  }

  return (
    <div className="bb-admin-qna__answer-form">
      <textarea
        className="bb-admin-qna__answer-textarea"
        value={answer}
        onChange={(event) => setAnswer(event.target.value)}
        aria-label={`문의 ${item.qnaId} 답변`}
      />
      <div className="bb-admin-qna__answer-actions">
        <button type="button" className="bb-admin-qna__action" onClick={() => setAnswering(false)}>
          취소
        </button>
        <Button
          variant="primary"
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending || !answer.trim()}
          loading={mutation.isPending}
        >
          등록
        </Button>
      </div>
    </div>
  );
}

/**
 * 관리자 문의 관리 `/admin/qna` — POST /admin/qna/{qnaId}/answer(AdminQnaController)로 답변을 단다.
 *
 * Task 4-14a가 admin 전용 전체 목록(GET /admin/qna)을 신설했다 — 4-14는 이 엔드포인트가 없어
 * goodsNo를 손으로 입력해 공개 목록(GET /qna)을 상품별로 검색해 재사용했었다. 공개 목록은
 * 비밀글 본문을 "비밀글입니다."로 마스킹하므로 admin이 답변해야 할 비밀글 내용을 볼 수 없는
 * 문제가 있었다(Task 4-14 KNOWN GAP). 이 배선으로 두 가지가 바뀐다:
 * (1) goodsNo 손입력 검색 UI를 제거하고 전체 문의를 한 번에 페이지로 받는다 — 대신 공개
 *     목록엔 없던 goodsNo를 열로 노출해 어느 상품의 문의인지 알 수 있게 한다.
 * (2) `AdminQnaResponse.question`은 서버가 마스킹하지 않고 그대로 낸다 — 비밀글도 본문이
 *     보이되, `isSecret`으로 "비밀글" 표시를 얹어 admin이 인지할 수 있게 한다.
 *
 * **페이지네이션(리뷰 반영)**: 전사 목록으로 바뀌며 goodsNo 필터가 없어져 0페이지 이상으로
 * 넘어가는 일이 흔해졌다(백엔드 기본 페이지 크기는 QnaService.DEFAULT_PAGE_SIZE=10). "이전/다음"
 * 버튼만 있는 최소 형태 — 서버가 내려주는 `PageResponse.hasNext`/`totalPages`로 경계에서
 * 비활성 처리한다.
 */
export function AdminQna() {
  const [page, setPage] = useState(0);
  const query = useQuery({ queryKey: ['admin-qna', page], queryFn: () => fetchAdminQna({ page }) });

  const items = query.data?.content ?? [];
  const totalPages = query.data?.totalPages ?? 0;
  const hasNext = query.data?.hasNext ?? false;

  return (
    <div className="bb-admin-qna">
      <h2 className="bb-admin-qna__title">문의 관리</h2>

      {query.isLoading && <Skeleton ratio="16 / 5" />}

      {query.isError && <p className="bb-admin-qna__error">문의 목록을 불러오지 못했어요.</p>}

      {query.isSuccess && items.length === 0 && <p className="bb-admin-qna__hint">등록된 문의가 없어요.</p>}

      {items.length > 0 && (
        <>
          <div className="bb-admin-qna__table-wrap">
            <table className="bb-admin-qna__table">
              <thead>
                <tr>
                  <th scope="col">상품번호</th>
                  <th scope="col">상태</th>
                  <th scope="col">질문</th>
                  <th scope="col">액션</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.qnaId} className="bb-admin-qna__row">
                    <td>{item.goodsNo}</td>
                    <td>{STATUS_LABEL[item.status] ?? item.status}</td>
                    <td className="bb-admin-qna__question-cell">
                      {item.isSecret && <span className="bb-admin-qna__secret-badge">비밀글</span>}
                      <span>{item.question}</span>
                    </td>
                    <td>
                      <AnswerCell item={item} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="bb-admin-qna__pagination">
            <button
              type="button"
              className="bb-admin-qna__action"
              onClick={() => setPage((prev) => prev - 1)}
              disabled={page === 0}
            >
              이전
            </button>
            <span className="bb-admin-qna__pagination-status">
              {page + 1} / {Math.max(totalPages, 1)}
            </span>
            <button
              type="button"
              className="bb-admin-qna__action"
              onClick={() => setPage((prev) => prev + 1)}
              disabled={!hasNext}
            >
              다음
            </button>
          </div>
        </>
      )}
    </div>
  );
}

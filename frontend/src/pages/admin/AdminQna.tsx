import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchQna } from '../../api/qna';
import { answerAdminQna } from '../../api/admin';
import type { QnaItem } from '../../types/review';
import { Button } from '../../components/ui/Button';
import { Field } from '../../components/ui/Field';
import { Skeleton } from '../../components/ui/Skeleton';
import { useToast } from '../../components/ui/useToast';
import './AdminQna.css';

/** 실제 백엔드 값은 WAITING이다(backend/.../qna/Qna.java:45,62) — QnaList.tsx와 동일 라벨. */
const STATUS_LABEL: Record<string, string> = {
  ANSWERED: '답변완료',
  WAITING: '답변대기',
};

function AnswerCell({ item, goodsNo }: { item: QnaItem; goodsNo: number }) {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [answering, setAnswering] = useState(false);
  const [answer, setAnswer] = useState('');

  const mutation = useMutation({
    mutationFn: () => answerAdminQna(item.qnaId, answer),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-qna', goodsNo] });
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
 * KNOWN GAP: admin 전용 문의 "전체" 목록 엔드포인트가 없다(AdminQnaController에는 answer 하나뿐).
 * 그래서 이 화면은 실제로 존재하는 공개 목록 GET /qna(goodsNo 필수)를 상품번호로 검색해 재사용한다.
 * 비밀글은 QnaService.visibleQuestion 기준으로 작성자 본인에게만 보이므로(admin 예외 없음),
 * admin이 조회해도 비밀글 본문은 "비밀글입니다."로 마스킹된 채 내려온다 — api/admin.ts의
 * answerAdminQna 문서 주석 참고.
 */
export function AdminQna() {
  const [goodsNoInput, setGoodsNoInput] = useState('');
  const [searchedGoodsNo, setSearchedGoodsNo] = useState<number | null>(null);

  const query = useQuery({
    queryKey: ['admin-qna', searchedGoodsNo],
    queryFn: () => fetchQna(searchedGoodsNo as number),
    enabled: searchedGoodsNo !== null,
  });

  function handleSearch() {
    const parsed = Number(goodsNoInput);
    if (Number.isFinite(parsed) && parsed > 0) {
      setSearchedGoodsNo(parsed);
    }
  }

  const items = query.data?.content ?? [];

  return (
    <div className="bb-admin-qna">
      <h2 className="bb-admin-qna__title">문의 관리</h2>

      <div className="bb-admin-qna__search">
        <Field
          id="admin-qna-goods-no"
          label="상품번호(goodsNo)"
          type="number"
          inputMode="numeric"
          value={goodsNoInput}
          onChange={setGoodsNoInput}
        />
        <Button variant="primary" onClick={handleSearch}>
          조회
        </Button>
      </div>

      {searchedGoodsNo === null && <p className="bb-admin-qna__hint">상품번호로 문의를 조회하세요.</p>}

      {searchedGoodsNo !== null && query.isLoading && <Skeleton ratio="16 / 5" />}

      {searchedGoodsNo !== null && query.isError && (
        <p className="bb-admin-qna__error">문의 목록을 불러오지 못했어요.</p>
      )}

      {searchedGoodsNo !== null && query.isSuccess && items.length === 0 && (
        <p className="bb-admin-qna__hint">이 상품에 등록된 문의가 없어요.</p>
      )}

      {searchedGoodsNo !== null && items.length > 0 && (
        <div className="bb-admin-qna__table-wrap">
          <table className="bb-admin-qna__table">
            <thead>
              <tr>
                <th scope="col">상태</th>
                <th scope="col">질문</th>
                <th scope="col">액션</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.qnaId} className="bb-admin-qna__row">
                  <td>{STATUS_LABEL[item.status] ?? item.status}</td>
                  <td>{item.isSecret ? '비밀글입니다.' : item.question}</td>
                  <td>
                    <AnswerCell item={item} goodsNo={searchedGoodsNo} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

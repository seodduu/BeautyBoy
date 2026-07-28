import { useQuery } from '@tanstack/react-query';
import { fetchQna } from '../../api/qna';
import { qnaStatusLabel } from '../../features/qna/status';
import { EmptyState } from '../common/EmptyState';
import { ErrorState } from '../common/ErrorState';
import { Skeleton } from '../ui/Skeleton';
import { QnaForm } from './QnaForm';
import './QnaList.css';

interface QnaListProps {
  goodsNo: number;
  /** Q&A 탭이 활성 상태일 때만 true — 비활성 탭에서는 fetch를 막는다(enabled). */
  active: boolean;
}

/** YYYY.MM.DD 포맷 — ReviewList와 동일 규약. */
function formatDate(iso: string): string {
  const date = new Date(iso);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}.${month}.${day}`;
}

/** Q&A 탭 — fetchQna 목록을 읽기 전용으로 보여준다(작성 불가). */
export function QnaList({ goodsNo, active }: QnaListProps) {
  const query = useQuery({
    queryKey: ['qna', goodsNo],
    queryFn: () => fetchQna(goodsNo),
    enabled: active,
  });

  if (!active) {
    return null;
  }

  if (query.isLoading) {
    return (
      <div className="bb-qna-list">
        <QnaForm goodsNo={goodsNo} />
        <div aria-hidden="true">
          <Skeleton ratio="6 / 1" />
          <Skeleton ratio="6 / 1" />
        </div>
      </div>
    );
  }

  if (query.isError) {
    return (
      <div className="bb-qna-list">
        <QnaForm goodsNo={goodsNo} />
        <ErrorState title="문의를 불러오지 못했어요" onRetry={() => query.refetch()} />
      </div>
    );
  }

  const items = query.data?.content ?? [];

  if (items.length === 0) {
    return (
      <div className="bb-qna-list">
        <QnaForm goodsNo={goodsNo} />
        <EmptyState title="아직 등록된 문의가 없어요" />
      </div>
    );
  }

  return (
    <div className="bb-qna-list">
      <QnaForm goodsNo={goodsNo} />
      <ul className="bb-qna-list__items">
        {items.map((item) => (
          <li key={item.qnaId} className="bb-qna-list__item">
            <div className="bb-qna-list__meta">
              {item.isSecret && <span className="bb-qna-list__secret">비밀글</span>}
              <span className="bb-qna-list__status">{qnaStatusLabel(item.status)}</span>
              <span className="bb-qna-list__date">{formatDate(item.createdAt)}</span>
            </div>
            <p className="bb-qna-list__question">{item.isSecret ? '비밀글입니다.' : item.question}</p>
          </li>
        ))}
      </ul>
    </div>
  );
}

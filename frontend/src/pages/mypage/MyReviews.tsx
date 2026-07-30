import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { deleteReview, fetchMyReviews, updateReview, type MyReviewItem } from '../../api/review';
import { queryKeys } from '../../api/queryKeys';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';
import { Skeleton } from '../../components/ui/Skeleton';
import { Button } from '../../components/ui/Button';
import { useToast } from '../../components/ui/useToast';
import { useTitle } from '../../hooks/useTitle';
import './MyReviews.css';

const STAR_VALUES = [1, 2, 3, 4, 5];

/** YYYY.MM.DD 포맷 — ReviewList와 같은 날짜 표기 규약(DESIGN.md 접근성 규약). */
function formatDate(iso: string): string {
  const date = new Date(iso);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}.${month}.${day}`;
}

/**
 * 마이페이지 내 리뷰 `/mypage/reviews` — 내가 쓴 리뷰를 상품명·썸네일과 함께 보여주고,
 * 그 자리에서 수정·삭제할 수 있다(설계 §2.8).
 *
 * 수정은 인라인 편집(모달 아님) — 별점 5개 + textarea가 그 항목 자리에서 열린다.
 * 삭제는 되돌릴 수 없어 확인 절차가 필수인데, `window.confirm`은 DESIGN.md 밖의 시각 언어라
 * 쓰지 않는다 — 대신 같은 자리에서 "정말 삭제할까요?"로 바뀌는 인라인 확인을 쓴다.
 *
 * 낙관적 갱신을 쓰지 않는다 — 성공 후 `queryKeys.myReviews()`를 무효화해 서버 값을 다시 받는다.
 * 수정·삭제는 3부(장바구니 수량)와 달리 연타 대상이 아니고, 삭제는 이미 확인 절차를 한 번
 * 거쳐 사용자가 결과를 기다릴 준비가 된 자리다.
 */
export function MyReviews() {
  useTitle('내 리뷰');
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const reviewsQuery = useQuery({ queryKey: queryKeys.myReviews(), queryFn: () => fetchMyReviews() });

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editRating, setEditRating] = useState(0);
  const [editContent, setEditContent] = useState('');
  const [confirmingDeleteId, setConfirmingDeleteId] = useState<number | null>(null);

  const updateMutation = useMutation({
    mutationFn: ({ reviewId, rating, content }: { reviewId: number; rating: number; content: string }) =>
      updateReview(reviewId, { rating, content }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.myReviews() });
      setEditingId(null);
      toast('수정했어요');
    },
    onError: () => toast('수정하지 못했어요. 다시 시도해 주세요', { tone: 'danger' }),
  });

  const deleteMutation = useMutation({
    mutationFn: (reviewId: number) => deleteReview(reviewId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.myReviews() });
      setConfirmingDeleteId(null);
      toast('삭제했어요');
    },
    onError: () => toast('삭제하지 못했어요. 다시 시도해 주세요', { tone: 'danger' }),
  });

  function openEdit(review: MyReviewItem) {
    setConfirmingDeleteId(null);
    setEditingId(review.reviewId);
    setEditRating(review.rating);
    setEditContent(review.content);
  }

  function closeEdit() {
    setEditingId(null);
  }

  function saveEdit(reviewId: number) {
    if (updateMutation.isPending) return;
    updateMutation.mutate({ reviewId, rating: editRating, content: editContent });
  }

  function openDeleteConfirm(reviewId: number) {
    setEditingId(null);
    setConfirmingDeleteId(reviewId);
  }

  function closeDeleteConfirm() {
    setConfirmingDeleteId(null);
  }

  if (reviewsQuery.isLoading) {
    return (
      <div className="bb-my-reviews">
        <Skeleton ratio="16 / 5" />
      </div>
    );
  }

  if (reviewsQuery.isError) {
    return (
      <div className="bb-my-reviews">
        <ErrorState title="내 리뷰를 불러오지 못했어요" onRetry={() => reviewsQuery.refetch()} />
      </div>
    );
  }

  const reviews = reviewsQuery.data?.content ?? [];

  if (reviews.length === 0) {
    return (
      <div className="bb-my-reviews">
        <EmptyState title="아직 작성한 리뷰가 없어요" description="구매한 상품에 리뷰를 남겨보세요." />
      </div>
    );
  }

  return (
    <ul className="bb-my-reviews__list">
      {reviews.map((review) => {
        const isEditing = editingId === review.reviewId;
        const isConfirmingDelete = confirmingDeleteId === review.reviewId;
        const contentId = `bb-my-reviews-edit-content-${review.reviewId}`;

        return (
          <li key={review.reviewId} className="bb-my-reviews__item">
            <Link to={`/goods/${review.goodsNo}`} className="bb-my-reviews__thumb-link">
              <img
                src={review.thumbnailUrl}
                alt={review.goodsName}
                loading="lazy"
                className="bb-my-reviews__thumb"
              />
            </Link>
            <div className="bb-my-reviews__body">
              <Link to={`/goods/${review.goodsNo}`} className="bb-my-reviews__goods-name">
                {review.goodsName}
              </Link>

              {isEditing ? (
                <div className="bb-my-reviews__edit">
                  <div className="bb-my-reviews__edit-stars" role="group" aria-label="별점">
                    {STAR_VALUES.map((value) => (
                      <button
                        key={value}
                        type="button"
                        className={`bb-my-reviews__edit-star${
                          value <= editRating ? ' bb-my-reviews__edit-star--active' : ''
                        }`}
                        aria-pressed={value <= editRating}
                        aria-label={`별점 ${value}점`}
                        onClick={() => setEditRating(value)}
                      >
                        ★
                      </button>
                    ))}
                  </div>
                  <label className="bb-my-reviews__edit-label" htmlFor={contentId}>
                    리뷰 내용
                  </label>
                  <textarea
                    id={contentId}
                    className="bb-my-reviews__edit-textarea"
                    value={editContent}
                    onChange={(event) => setEditContent(event.target.value)}
                  />
                  <div className="bb-my-reviews__edit-actions">
                    <Button
                      variant="primary"
                      loading={updateMutation.isPending}
                      disabled={updateMutation.isPending || editRating === 0 || editContent.trim().length === 0}
                      onClick={() => saveEdit(review.reviewId)}
                    >
                      저장
                    </Button>
                    <Button variant="ghost" disabled={updateMutation.isPending} onClick={closeEdit}>
                      취소
                    </Button>
                  </div>
                </div>
              ) : (
                <>
                  <div className="bb-my-reviews__meta">
                    <span className="bb-my-reviews__rating">{review.rating.toFixed(1)}</span>
                    <span className="bb-my-reviews__date">{formatDate(review.createdAt)}</span>
                  </div>
                  <p className="bb-my-reviews__content">{review.content}</p>
                  <p className="bb-my-reviews__helpful">
                    도움돼요 {review.helpfulCount.toLocaleString('ko-KR')}
                  </p>

                  {isConfirmingDelete ? (
                    <div className="bb-my-reviews__delete-confirm">
                      <p className="bb-my-reviews__delete-confirm-text">정말 삭제할까요?</p>
                      <div className="bb-my-reviews__delete-confirm-actions">
                        {/* 확정하는 쪽이 primary다 — CancelOrderModal의 "취소 확정"과 같은 규약.
                            둘 다 ghost로 두면 되돌릴 수 없는 쪽과 무해한 쪽이 구별되지 않는다. */}
                        <Button
                          variant="primary"
                          loading={deleteMutation.isPending}
                          disabled={deleteMutation.isPending}
                          onClick={() => deleteMutation.mutate(review.reviewId)}
                        >
                          삭제
                        </Button>
                        <Button
                          variant="ghost"
                          disabled={deleteMutation.isPending}
                          onClick={closeDeleteConfirm}
                        >
                          취소
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <div className="bb-my-reviews__actions">
                      <Button variant="ghost" onClick={() => openEdit(review)}>
                        수정
                      </Button>
                      <Button variant="ghost" onClick={() => openDeleteConfirm(review.reviewId)}>
                        삭제
                      </Button>
                    </div>
                  )}
                </>
              )}
            </div>
          </li>
        );
      })}
    </ul>
  );
}

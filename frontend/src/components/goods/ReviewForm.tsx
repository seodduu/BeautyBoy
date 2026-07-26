import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createReview } from '../../api/review';
import { useAuthStore } from '../../stores/authStore';
import { Button } from '../ui/Button';
import './ReviewForm.css';

interface ReviewFormProps {
  goodsNo: number;
}

const STAR_VALUES = [1, 2, 3, 4, 5];

/**
 * 상세 페이지 리뷰 탭 상단의 작성 폼 — 별점(1~5) + 본문.
 * 비로그인이면 폼 대신 로그인 유도 안내를 보여준다(RequireAuth가 /goods/:goodsNo 자체를
 * 이미 감싸지만, 상세 페이지 자체는 인증 없이 못 들어오므로 이 분기는 방어적이다 —
 * 다만 브리프가 명시적으로 요구하는 사양이라 그대로 둔다).
 * 성공하면 ReviewList가 쓰는 것과 같은 queryKey(['reviews', goodsNo], ['review-stats', goodsNo])를
 * 무효화해 실제로 재조회를 트리거하고, 폼을 초기값으로 비운다.
 */
export function ReviewForm({ goodsNo }: ReviewFormProps) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const queryClient = useQueryClient();

  const [rating, setRating] = useState(0);
  const [content, setContent] = useState('');
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => createReview({ goodsNo, rating, content }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews', goodsNo] });
      queryClient.invalidateQueries({ queryKey: ['review-stats', goodsNo] });
      setRating(0);
      setContent('');
      setError(null);
    },
    onError: (err: unknown) => {
      if (isAxiosError(err) && typeof err.response?.data?.message === 'string') {
        setError(err.response.data.message);
      } else {
        setError('리뷰를 등록하지 못했어요. 잠시 후 다시 시도해 주세요.');
      }
    },
  });

  if (!accessToken) {
    return (
      <div className="bb-review-form bb-review-form--guest">
        <p className="bb-review-form__guest-text">
          로그인하면 구매한 상품에 리뷰를 남길 수 있어요.
        </p>
        <Link to="/login" className="bb-review-form__login-link">
          로그인하러 가기
        </Link>
      </div>
    );
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (mutation.isPending) {
      return;
    }
    if (rating === 0) {
      setError('별점을 선택해 주세요.');
      return;
    }
    setError(null);
    mutation.mutate();
  }

  return (
    <form className="bb-review-form" onSubmit={handleSubmit}>
      <div className="bb-review-form__stars" role="group" aria-label="별점">
        {STAR_VALUES.map((value) => (
          <button
            key={value}
            type="button"
            className={`bb-review-form__star${value <= rating ? ' bb-review-form__star--active' : ''}`}
            aria-pressed={value <= rating}
            aria-label={`별점 ${value}점`}
            onClick={() => setRating(value)}
          >
            ★
          </button>
        ))}
      </div>

      <label className="bb-review-form__label" htmlFor="bb-review-content">
        리뷰 내용
      </label>
      <textarea
        id="bb-review-content"
        className="bb-review-form__textarea"
        value={content}
        onChange={(event) => setContent(event.target.value)}
        placeholder="사용해보니 어땠는지 남겨 주세요."
      />

      {error && (
        <p role="alert" className="bb-review-form__error">
          {error}
        </p>
      )}

      <Button
        type="submit"
        variant="primary"
        className="bb-review-form__submit"
        loading={mutation.isPending}
        disabled={mutation.isPending}
      >
        등록
      </Button>
    </form>
  );
}

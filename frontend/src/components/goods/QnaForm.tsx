import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createQna } from '../../api/qna';
import { useAuthStore } from '../../stores/authStore';
import { Button } from '../ui/Button';
import './QnaForm.css';

interface QnaFormProps {
  goodsNo: number;
}

/**
 * 상세 페이지 Q&A 탭 상단의 문의 작성 폼 — 본문 + 비밀글 여부.
 * ReviewForm과 같은 계약: 비로그인이면 폼 대신 로그인 안내, 성공하면 QnaList가 쓰는
 * queryKey(['qna', goodsNo])를 무효화해 실제로 재조회를 트리거하고 폼을 비운다.
 */
export function QnaForm({ goodsNo }: QnaFormProps) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const queryClient = useQueryClient();

  const [question, setQuestion] = useState('');
  const [isSecret, setIsSecret] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => createQna({ goodsNo, question, isSecret }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['qna', goodsNo] });
      setQuestion('');
      setIsSecret(false);
      setError(null);
    },
    onError: (err: unknown) => {
      if (isAxiosError(err) && typeof err.response?.data?.message === 'string') {
        setError(err.response.data.message);
      } else {
        setError('문의를 등록하지 못했어요. 잠시 후 다시 시도해 주세요.');
      }
    },
  });

  if (!accessToken) {
    return (
      <div className="bb-qna-form bb-qna-form--guest">
        <p className="bb-qna-form__guest-text">로그인하면 상품 문의를 남길 수 있어요.</p>
        <Link to="/login" className="bb-qna-form__login-link">
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
    if (!question.trim()) {
      setError('문의 내용을 입력해 주세요.');
      return;
    }
    setError(null);
    mutation.mutate();
  }

  return (
    <form className="bb-qna-form" onSubmit={handleSubmit}>
      <label className="bb-qna-form__label" htmlFor="bb-qna-question">
        문의 내용
      </label>
      <textarea
        id="bb-qna-question"
        className="bb-qna-form__textarea"
        value={question}
        onChange={(event) => setQuestion(event.target.value)}
        placeholder="궁금한 점을 남겨 주세요."
      />

      <label className="bb-qna-form__secret">
        <input
          type="checkbox"
          checked={isSecret}
          onChange={(event) => setIsSecret(event.target.checked)}
        />
        비밀글로 작성
      </label>

      {error && (
        <p role="alert" className="bb-qna-form__error">
          {error}
        </p>
      )}

      <Button
        type="submit"
        variant="primary"
        className="bb-qna-form__submit"
        loading={mutation.isPending}
        disabled={mutation.isPending}
      >
        등록
      </Button>
    </form>
  );
}

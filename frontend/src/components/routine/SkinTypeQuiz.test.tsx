import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { QUIZ, scoreQuiz, SkinTypeQuiz } from './SkinTypeQuiz';

describe('scoreQuiz — 가중치 합산', () => {
  // 전체 조합(4×3×3=36가지) 계산표로 검증한 두 대표 케이스.
  // 브리프 원문의 [0,1,0]→'DRY', [2,2,0]→동점 기대값은 QUIZ 가중치로 재계산하면 성립하지 않아
  // (오케스트레이터 사전 조사로) 아래처럼 바로잡았다. 계산 근거는 태스크 리포트 참고.
  it('최다 득표 피부타입을 고른다', () => {
    // Q1 opt0 DRY+2, Q2 opt1 OILY+2, Q3 opt0 OILY+1·COMBINATION+1
    // → DRY 2 / OILY 3 / COMBINATION 1 / SENSITIVE 0 → 단독 최다 OILY.
    expect(scoreQuiz([0, 1, 0])).toBe('OILY');
  });

  it('단독 최다면 동점 규칙을 타지 않는다', () => {
    // Q1 opt2 COMBINATION+2, Q2 opt2 COMBINATION+2, Q3 opt0 OILY+1·COMBINATION+1
    // → COMBINATION 5로 단독 최다(동점 아님).
    expect(scoreQuiz([2, 2, 0])).toBe('COMBINATION');
  });

  it('동점이면 COMBINATION으로 떨어진다', () => {
    // Q1 opt0 DRY+2, Q2 opt0 DRY+1·SENSITIVE+1, Q3 opt2 SENSITIVE+2
    // → DRY 3 / SENSITIVE 3 → 동점 → COMBINATION.
    expect(scoreQuiz([0, 0, 2])).toBe('COMBINATION');
  });

  it('문항 수만큼 응답하지 않아도(범위 밖 인덱스는 건너뛰고) 죽지 않는다', () => {
    expect(() => scoreQuiz([0])).not.toThrow();
  });
});

describe('SkinTypeQuiz — 문항 렌더·진행', () => {
  it('첫 문항부터 보여준다', () => {
    render(<SkinTypeQuiz onComplete={vi.fn()} />);
    expect(screen.getByText(QUIZ[0].question)).toBeInTheDocument();
  });

  it('문항을 답하면 다음 문항으로 넘어간다', () => {
    render(<SkinTypeQuiz onComplete={vi.fn()} />);
    fireEvent.click(screen.getByRole('radio', { name: QUIZ[0].options[0].label }));
    expect(screen.getByText(QUIZ[1].question)).toBeInTheDocument();
  });

  it('세 문항 모두 답하면 채점 결과로 onComplete를 부른다', () => {
    const onComplete = vi.fn();
    render(<SkinTypeQuiz onComplete={onComplete} />);

    fireEvent.click(screen.getByRole('radio', { name: QUIZ[0].options[0].label })); // DRY:2
    fireEvent.click(screen.getByRole('radio', { name: QUIZ[1].options[0].label })); // DRY:1,SENSITIVE:1
    fireEvent.click(screen.getByRole('radio', { name: QUIZ[2].options[0].label })); // OILY:1,COMBINATION:1

    // DRY 3 / OILY 1 / COMBINATION 1 / SENSITIVE 1 → 단독 최다 DRY.
    expect(onComplete).toHaveBeenCalledWith('DRY');
  });
});

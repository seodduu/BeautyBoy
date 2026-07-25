import { useState } from 'react';
import type { SkinType } from '../../api/routine';
import './SkinTypeQuiz.css';

interface QuizOption {
  label: string;
  weight: Partial<Record<SkinType, number>>;
}

interface QuizQuestion {
  question: string;
  options: QuizOption[];
}

/**
 * 피부타입 퀴즈 3문항 — 설계 8장 "비회원=3문항 퀴즈". 문항·가중치는 이 상수 하나가 유일한 진실이다.
 * 세수 후 30분 뒤 / 오후 T존 / 자극 반응 — 이 셋이 피부타입을 가르는 최소 질문이다.
 */
export const QUIZ: QuizQuestion[] = [
  {
    question: '세수하고 30분 뒤 얼굴은 어떤가요?',
    options: [
      { label: '당기고 각질이 인다', weight: { DRY: 2 } },
      { label: '번들거린다', weight: { OILY: 2 } },
      { label: 'T존만 번들거린다', weight: { COMBINATION: 2 } },
      { label: '붉어지거나 따갑다', weight: { SENSITIVE: 2 } },
    ],
  },
  {
    question: '오후가 되면 이마·코가…',
    options: [
      { label: '거의 그대로다', weight: { DRY: 1, SENSITIVE: 1 } },
      { label: '기름종이가 필요하다', weight: { OILY: 2 } },
      { label: 'T존만 필요하다', weight: { COMBINATION: 2 } },
    ],
  },
  {
    question: '새 화장품을 쓰면?',
    options: [
      { label: '별 반응 없다', weight: { OILY: 1, COMBINATION: 1 } },
      { label: '가끔 뒤집어진다', weight: { SENSITIVE: 1 } },
      { label: '자주 따갑고 붉어진다', weight: { SENSITIVE: 2 } },
    ],
  },
];

const SKIN_TYPES: readonly SkinType[] = ['DRY', 'OILY', 'COMBINATION', 'SENSITIVE'];

/**
 * 문항별 선택지 인덱스 배열을 받아 최다 득표 피부타입을 고른다.
 * 동점이면 COMBINATION(가장 무난한 기본값)으로 떨어뜨린다.
 * 범위를 벗어난(응답 안 한) 문항은 집계에서 건너뛴다.
 */
export function scoreQuiz(answers: number[]): SkinType {
  const totals: Record<SkinType, number> = { DRY: 0, OILY: 0, COMBINATION: 0, SENSITIVE: 0 };

  QUIZ.forEach((question, index) => {
    const option = question.options[answers[index]];
    if (!option) {
      return;
    }
    for (const type of SKIN_TYPES) {
      totals[type] += option.weight[type] ?? 0;
    }
  });

  const max = Math.max(...SKIN_TYPES.map((type) => totals[type]));
  const winners = SKIN_TYPES.filter((type) => totals[type] === max);
  return winners.length === 1 ? winners[0] : 'COMBINATION';
}

interface SkinTypeQuizProps {
  onComplete: (skinType: SkinType) => void;
}

/**
 * 3문항을 한 번에 하나씩 보여주고, 마지막 문항에 답하면 채점해 onComplete로 넘긴다.
 * role="radiogroup" 하나가 문항 하나 — 문항이 바뀔 때 라디오 그룹이 통째로 새로 그려지므로
 * (key={currentIndex}) 이전 문항의 선택 상태가 남지 않는다.
 */
export function SkinTypeQuiz({ onComplete }: SkinTypeQuizProps) {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState<number[]>([]);

  const current = QUIZ[currentIndex];

  function handleSelect(optionIndex: number) {
    const nextAnswers = [...answers.slice(0, currentIndex), optionIndex];

    if (currentIndex === QUIZ.length - 1) {
      onComplete(scoreQuiz(nextAnswers));
      return;
    }

    setAnswers(nextAnswers);
    setCurrentIndex((index) => index + 1);
  }

  return (
    <div className="bb-skin-quiz">
      <p className="bb-skin-quiz__progress">
        {currentIndex + 1} / {QUIZ.length}
      </p>
      <h2 className="bb-skin-quiz__question">{current.question}</h2>
      <div
        className="bb-skin-quiz__options"
        role="radiogroup"
        aria-label={current.question}
        key={currentIndex}
      >
        {current.options.map((option, optionIndex) => (
          <label key={option.label} className="bb-skin-quiz__option">
            <input
              type="radio"
              className="bb-skin-quiz__input"
              name={`quiz-question-${currentIndex}`}
              value={optionIndex}
              checked={false}
              onChange={() => handleSelect(optionIndex)}
            />
            <span className="bb-skin-quiz__label">{option.label}</span>
          </label>
        ))}
      </div>
    </div>
  );
}

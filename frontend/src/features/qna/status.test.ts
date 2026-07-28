import { describe, expect, it } from 'vitest';
import { qnaStatusLabel } from './status';

describe('qnaStatusLabel', () => {
  it('백엔드 상태 문자열을 한글 표시명으로 바꾼다', () => {
    expect(qnaStatusLabel('WAITING')).toBe('답변대기');
    expect(qnaStatusLabel('ANSWERED')).toBe('답변완료');
  });

  it('미지 코드는 코드를 그대로 돌려준다 — 칸이 비면 상태 없는 문의로 읽힌다', () => {
    expect(qnaStatusLabel('CLOSED')).toBe('CLOSED');
  });
});

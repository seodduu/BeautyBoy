import { useEffect } from 'react';

// 접미사는 이 훅 한 곳에서만 붙인다(설계 §1.2) — 호출부가 직접 적으면 20곳 중 하나는 반드시 어긋난다.
const SUFFIX = '뷰티보이';

/**
 * 페이지별 `document.title` 설정 훅.
 *
 * - `title`이 `null`/`undefined`면 아무것도 하지 않는다 — 이것이 "로딩 중" 표현이다.
 *   호출부는 `useTitle(goods?.name)`처럼 분기 없이 부를 수 있다.
 * - 언마운트에서 이전 제목으로 되돌리지 않는다 — 되돌리면 라우트 전환 중 기본 타이틀이
 *   한 번 깜빡인다. 다음 화면이 항상 자기 제목을 세우므로 되돌릴 이유가 없다.
 */
export function useTitle(title: string | null | undefined): void {
  useEffect(() => {
    if (title === null || title === undefined) {
      return;
    }
    document.title = `${title} | ${SUFFIX}`;
  }, [title]);
}

import './Pager.css';

/** 현재 페이지 좌우로 보여줄 번호 개수 — DESIGN.md `pager`의 "현재 페이지 ±2, 최대 5개". */
const WINDOW_RADIUS = 2;
const WINDOW_SIZE = WINDOW_RADIUS * 2 + 1;

interface PagerProps {
  /** 1-based 현재 페이지 */
  page: number;
  totalPages: number;
  /** 클릭된 1-based 페이지 번호를 넘긴다 — URL 갱신·스크롤은 호출부 책임 */
  onPageChange: (page: number) => void;
}

/**
 * 현재 페이지를 가운데 두는 번호 윈도. 양 끝에서는 밖으로 넘치는 대신 안쪽으로 민다 —
 * 마지막 페이지에서 번호가 하나만 남으면 "여기가 끝"이 아니라 "덜 불러왔다"로 읽힌다.
 */
function pageWindow(page: number, totalPages: number): number[] {
  const size = Math.min(WINDOW_SIZE, totalPages);
  const start = Math.min(Math.max(page - WINDOW_RADIUS, 1), totalPages - size + 1);
  return Array.from({ length: size }, (_, i) => start + i);
}

/**
 * 상품 그리드 아래 중앙 번호 페이저 (DESIGN.md `pager`). 목록·검색 공용으로 만들되
 * 상태는 갖지 않는다 — 페이지의 진실은 URL이고, 이 컴포넌트는 클릭을 1-based로 올릴 뿐이다.
 *
 * **1페이지뿐이면 아무것도 렌더하지 않는다**(자리도 남기지 않는다) — 그리드의 행 간격 리듬이
 * 그대로 페이지 끝이 된다.
 */
export function Pager({ page, totalPages, onPageChange }: PagerProps) {
  if (totalPages <= 1) return null;

  return (
    <nav className="bb-pager" aria-label="페이지 이동">
      <button
        type="button"
        className="bb-pager__step"
        disabled={page <= 1}
        onClick={() => onPageChange(page - 1)}
      >
        이전
      </button>

      {pageWindow(page, totalPages).map((number) => (
        <button
          key={number}
          type="button"
          className="bb-pager__number"
          // 색 반전만으로 현재 위치를 알리지 않는다 — aria-current가 보조기술의 단서다.
          aria-current={number === page ? 'page' : undefined}
          onClick={() => onPageChange(number)}
        >
          {number}
        </button>
      ))}

      <button
        type="button"
        className="bb-pager__step"
        disabled={page >= totalPages}
        onClick={() => onPageChange(page + 1)}
      >
        다음
      </button>
    </nav>
  );
}

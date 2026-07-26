import { Link } from 'react-router-dom';
import type { TagView } from '../../types/goods';
import './Tag.css';

interface TagProps {
  view: TagView;
  /** 있으면 해당 경로로 이동하는 링크로 렌더한다(예: 태그 필터). 없으면 순수 표시용 span. */
  to?: string;
}

/**
 * 상품 태그 pill. DESIGN.md 규칙: 배경 채움 금지, 텍스트/1px 테두리로만 구분.
 * EFFECT(효과) → 무채색 ink/graphite pill, TEXTURE(사용감) → stone(더 옅은 회색).
 * 숫자·signal-* 색(주황 주의색 등)은 쓰지 않는다 — 태그는 정보 라벨이지 경고가 아니다.
 */
export function Tag({ view, to }: TagProps) {
  const className = `bb-tag bb-tag--${view.kind === 'EFFECT' ? 'effect' : 'texture'}`;

  if (to) {
    return (
      <Link to={to} className={className}>
        {view.name}
      </Link>
    );
  }

  return <span className={className}>{view.name}</span>;
}

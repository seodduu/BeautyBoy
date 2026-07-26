import { Link } from 'react-router-dom';
import type { TagView } from '../../types/goods';
import './Tag.css';

interface TagProps {
  view: TagView;
  /** 있으면 해당 경로로 이동하는 링크로 렌더한다(예: 태그 필터). 없으면 순수 표시용 span. */
  to?: string;
}

/**
 * 상품 태그 pill. DESIGN.md "태그 컬러" 절: 태그 pill에 한해 무채색·배경 금지 규칙이 해제됐다 —
 * slug별 틴트 배경 + 진한 글자(bb-tag--{slug})로 렌더한다. 팔레트에 없는 slug나 TEXTURE는
 * kind 클래스(bb-tag--effect/property/texture)의 무채색 폴백으로 떨어진다(Tag.css 참조).
 * kind 클래스는 항상 함께 붙는다 — slug 클래스가 없거나 정의되지 않아도 렌더가 깨지지 않게 하기 위해서다.
 */
export function Tag({ view, to }: TagProps) {
  const kindClass =
    view.kind === 'EFFECT' ? 'effect' : view.kind === 'PROPERTY' ? 'property' : 'texture';
  const className = `bb-tag bb-tag--${kindClass} bb-tag--${view.slug}`;

  if (to) {
    return (
      <Link to={to} className={className}>
        {view.name}
      </Link>
    );
  }

  return <span className={className}>{view.name}</span>;
}

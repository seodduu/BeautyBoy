import './Skeleton.css';

interface SkeletonProps {
  /** 종횡비. 기본값은 media-thumbnail 규칙(1:1 정사각) — 상품 썸네일용. */
  ratio?: string;
  className?: string;
}

/**
 * DESIGN.md media-thumbnail의 surface-cool 플레이스홀더 규칙.
 * prefers-reduced-motion에서 애니메이션 정지.
 */
export function Skeleton({ ratio = '1 / 1', className }: SkeletonProps) {
  return (
    <div
      className={`bb-skeleton${className ? ` ${className}` : ''}`}
      style={{ aspectRatio: ratio }}
      aria-hidden="true"
    />
  );
}

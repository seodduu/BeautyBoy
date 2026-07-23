import type { BadgeType } from '../../types/goods';
import './Badge.css';

interface BadgeProps {
  type: BadgeType;
}

const LABEL: Record<BadgeType, string> = {
  SALE: 'SALE',
  COUPON: 'COUPON',
  GIFT: 'GIFT',
  ONE_PLUS_ONE: '1+1',
};

/**
 * DESIGN.md badge-sale / badge-neutral / badge-today-dream.
 * 흰 바탕 + 색 글자 + micro-caps. SALE만 signal-sale, 나머지는 graphite.
 * 배경 채움 금지.
 */
export function Badge({ type }: BadgeProps) {
  const variantClass = type === 'SALE' ? 'bb-badge--sale' : 'bb-badge--neutral';

  return <span className={`bb-badge ${variantClass}`}>{LABEL[type]}</span>;
}

/** 오늘드림 배지 — SALE/COUPON/GIFT/1+1과 별도 신호(signal-success)라 타입을 분리한다. */
export function TodayDreamBadge() {
  return <span className="bb-badge bb-badge--today-dream">오늘드림</span>;
}

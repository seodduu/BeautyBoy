import { Button } from '../ui/Button';
import './EmptyState.css';

interface EmptyStateProps {
  title: string;
  description?: string;
  action?: { label: string; onClick: () => void };
}

/**
 * DESIGN.md UX 계약: 빈 장바구니·찜·검색 무결과·주문 없음이 재사용하는 빈 상태.
 * 안내문(body/graphite) + 선택적 행동(Button ghost 변형). 배경 채움·시그널 색 없음(무채색).
 */
export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="bb-empty" role="status">
      <p className="bb-empty__title">{title}</p>
      {description && <p className="bb-empty__desc">{description}</p>}
      {action && (
        <Button variant="ghost" onClick={action.onClick}>
          {action.label}
        </Button>
      )}
    </div>
  );
}

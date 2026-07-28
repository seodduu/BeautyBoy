import { Button } from '../ui/Button';
import './ErrorState.css';

interface ErrorStateProps {
  title: string;
  description?: string;
  onRetry?: () => void;
}

const DEFAULT_DESCRIPTION = '잠시 후 다시 시도해 주세요';

/**
 * 쿼리 로딩 실패 분기가 재사용하는 오류 상태. `EmptyState`와 같은 형태(제목 + 설명 +
 * 선택적 행동, `Button variant="ghost"`)를 따르되 즉시 알림이 필요하므로 role="alert"을
 * 쓴다(EmptyState는 role="status"). DESIGN.md 액센트 규칙에 따라 signal-* 배경 채움 없음.
 */
export function ErrorState({ title, description, onRetry }: ErrorStateProps) {
  return (
    <div className="bb-error" role="alert">
      <p className="bb-error__title">{title}</p>
      <p className="bb-error__desc">{description ?? DEFAULT_DESCRIPTION}</p>
      {onRetry && (
        <Button variant="ghost" onClick={onRetry}>
          다시 시도
        </Button>
      )}
    </div>
  );
}

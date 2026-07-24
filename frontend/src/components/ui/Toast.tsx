import './Toast.css';

export type ToastTone = 'success' | 'danger';

export interface ToastItem {
  id: number;
  message: string;
  tone: ToastTone;
}

interface ToastProps {
  toast: ToastItem;
  /** prefers-reduced-motion이면 페이드/슬라이드 클래스를 붙이지 않는다. */
  animate: boolean;
}

const TONE_CLASS: Record<ToastTone, string> = {
  success: 'bb-toast--success',
  danger: 'bb-toast--danger',
};

/**
 * DESIGN.md UX 계약(§피드백): 배경은 --color-canvas 유지, 시그널 색은
 * 왼쪽 아이콘/보더 악센트 + 텍스트에만 쓰고 배경을 채우지 않는다.
 */
export function Toast({ toast, animate }: ToastProps) {
  return (
    <div
      className={`bb-toast ${TONE_CLASS[toast.tone]}${animate ? ' bb-toast--animate' : ''}`}
    >
      <span className="bb-toast__accent" aria-hidden="true" />
      <span className="bb-toast__message">{toast.message}</span>
    </div>
  );
}

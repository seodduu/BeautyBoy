import { createContext, useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Toast, type ToastItem, type ToastTone } from './Toast';
import './Toast.css';

const AUTO_DISMISS_MS = 3500;
let nextId = 0;

export interface ToastContextValue {
  toast: (message: string, opts?: { tone?: ToastTone }) => void;
}

export const ToastContext = createContext<ToastContextValue | null>(null);

/**
 * 장바구니 담기(2-3)·검색(2-4) 등에서 공유하는 토스트 큐 + 상시 라이브리전.
 * role=status/aria-live=polite 컨테이너는 토스트가 없어도 항상 DOM에 존재해야
 * 스크린리더가 이후 갱신을 놓치지 않는다.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const [reducedMotion, setReducedMotion] = useState(false);
  const timers = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());

  useEffect(() => {
    const mql = window.matchMedia('(prefers-reduced-motion: reduce)');
    setReducedMotion(mql.matches);
    const onChange = (e: MediaQueryListEvent) => setReducedMotion(e.matches);
    mql.addEventListener?.('change', onChange);
    return () => mql.removeEventListener?.('change', onChange);
  }, []);

  useEffect(() => {
    const activeTimers = timers.current;
    return () => {
      activeTimers.forEach((timer) => clearTimeout(timer));
      activeTimers.clear();
    };
  }, []);

  const dismiss = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
    const timer = timers.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timers.current.delete(id);
    }
  }, []);

  const toast = useCallback(
    (message: string, opts?: { tone?: ToastTone }) => {
      const id = nextId++;
      const tone = opts?.tone ?? 'success';
      setToasts((prev) => [...prev, { id, message, tone }]);
      const timer = setTimeout(() => dismiss(id), AUTO_DISMISS_MS);
      timers.current.set(id, timer);
    },
    [dismiss],
  );

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      <div className="bb-toast-region" role="status" aria-live="polite" aria-atomic="true">
        {toasts.map((t) => (
          <Toast key={t.id} toast={t} animate={!reducedMotion} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

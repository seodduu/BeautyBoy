import { useContext } from 'react';
import { ToastContext, type ToastContextValue } from './ToastProvider';

/**
 * <ToastProvider> 하위에서만 사용 가능. 바깥에서 호출하면 원인을 바로 알 수
 * 있도록 명확한 에러를 던진다.
 */
export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast는 <ToastProvider> 내부에서만 사용할 수 있습니다.');
  }
  return ctx;
}

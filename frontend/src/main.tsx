import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { ToastProvider } from './components/ui/ToastProvider'

/**
 * dev에서만, 그리고 VITE_USE_MOCK=true일 때만 msw worker를 시작한다.
 * Wave 3에서 실 API로 붙일 때는 이 환경변수 하나만 끄면 된다.
 * worker 시작은 비동기이므로 완료를 기다린 뒤 렌더한다 — 그렇지 않으면
 * 초기 렌더의 첫 요청이 워커 등록 전에 나가 mock을 못 맞고 실패한다.
 */
async function bootstrap() {
  if (import.meta.env.DEV && import.meta.env.VITE_USE_MOCK === 'true') {
    const { worker } = await import('./mocks/browser');
    await worker.start({ onUnhandledRequest: 'bypass' });
  }

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <ToastProvider>
        <App />
      </ToastProvider>
    </StrictMode>,
  );
}

bootstrap();

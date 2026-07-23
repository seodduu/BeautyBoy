import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

/**
 * 브라우저 전용 msw worker. `main.tsx`가 `VITE_USE_MOCK=true`인 dev에서만 시작한다.
 * 동작하려면 `public/mockServiceWorker.js`가 있어야 한다(`npm run msw:init`으로 생성).
 */
export const worker = setupWorker(...handlers);

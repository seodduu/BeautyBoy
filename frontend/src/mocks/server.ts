import { setupServer } from 'msw/node';

/**
 * 테스트 전용 msw 서버.
 * 각 테스트 파일에서 `server.use(...)`로 시나리오별 핸들러를 덧붙인다.
 */
export const server = setupServer();

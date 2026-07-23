import { setupServer } from 'msw/node';
import { handlers } from './handlers';

/**
 * 테스트 전용 msw 서버.
 * `handlers`(상품 목록/상세/카테고리 트리)를 공통 기본 핸들러로 등록한다.
 * `afterEach(() => server.resetHandlers())`는 이 기본 핸들러로 되돌아가므로,
 * 각 테스트 파일은 필요한 시나리오만 `server.use(...)`로 덧붙이면 된다.
 */
export const server = setupServer(...handlers);

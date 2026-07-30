import { defineConfig } from 'vitest/config';

/**
 * 벤치·골든케이스 전용 vitest 설정 — `npm test`와 완전히 갈라 둔다.
 *
 * `npm test`는 `vitest run --exclude 'e2e/**'`이고 기본 include가 `**\/*.{test,spec}.*`다.
 * 여기 파일은 전부 `*.bench.ts`라 그 패턴에 걸리지 않으므로 `npm test`가 절대 집지 않는다
 * (e2e 스펙이 프론트 전체 판정을 적신호로 만들던 일의 재발 방지).
 *
 * 루트 `vite.config.ts`는 공유 계약이라 건드리지 않는다 — 그래서 이 파일이 따로 있다.
 *
 * 실행: `cd frontend && npx vitest run --config bench/vitest.bench.config.ts`
 */
export default defineConfig({
  test: {
    include: ['bench/**/*.bench.ts'],
    // 재는 대상이 순수 계산부(localStorage·DOM 없음)라 jsdom이 필요 없다. 계측에 잡음도 적다.
    environment: 'node',
    globals: true,
    // 벤치는 1,000회 × 2지점을 돌린다. 기본 5초 타임아웃으론 부족하다.
    testTimeout: 120_000,
  },
});

import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: 0, // 재시도로 플래키를 가리지 않는다. 깨지면 원인을 본다.
  // 모든 스펙이 시드 계정 하나(dry@beautyboy.dev)의 장바구니·주문·재고를 공유한다. 파일별로
  // 워커를 나누면 checkout의 "장바구니 5줄"과 cancel의 담기가 서로를 덮어써 거짓 적신호가 난다 —
  // 공유 상태가 진짜 하나뿐이므로 병렬화하지 않는 것이 맞다.
  workers: 1,
  use: { baseURL: 'http://localhost:5173', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
  },
});

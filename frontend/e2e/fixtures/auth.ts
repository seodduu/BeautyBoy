import { expect, type Page } from '@playwright/test';

/**
 * 시드 계정(Task 4-15, `V64__seed_member.sql`)으로 로그인 화면을 거쳐 로그인한다.
 * 로그인 성공 시 `/main`으로 이동하는 것(Login.tsx 주석 참고)을 완료 신호로 쓴다 —
 * 리프레시 토큰은 httpOnly 쿠키로 내려오므로 이후 페이지 이동에서도 세션이 유지된다.
 *
 * 예전에는 여기서 `POST /auth/refresh` 동시 호출을 라우트 레벨에서 1건으로 합치는
 * `installAuthRefreshDedup` shim을 걸었다. 그 shim이 동시 refresh 경로를 아예 우회시켜
 * E2E가 결함을 못 보게 만들고 있었으므로 Task 4-16a에서 제거했다 — 이제 이 스펙은
 * StrictMode 이중 호출이 만드는 실제 동시 refresh를 그대로 통과한다.
 */
export async function loginAsSeedUser(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('이메일').fill(email);
  await page.getByLabel('비밀번호').fill(password);
  await page.getByRole('button', { name: '로그인' }).click();
  await expect(page).toHaveURL(/\/main/);
}

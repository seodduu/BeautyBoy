import { expect, type Page } from '@playwright/test';

/**
 * `POST /auth/refresh` 중복 동시 호출을 실제 백엔드 호출 1건으로 합친다.
 *
 * **왜 필요한가(실제 결함, out of scope — 보고서 "E2E가 드러낸 실제 결함" 절 참고):**
 * `npm run dev`(React 19 StrictMode)로 뜬 프론트는 하드 네비게이션마다 `App`의 부트스트랩
 * 이펙트가 두 번 실행돼 `POST /auth/refresh`가 거의 동시에 두 번 나간다. 백엔드
 * `AuthService.refresh()`는 리프레시 토큰을 조회 즉시 삭제하므로(재사용 방지), 두 트랜잭션이
 * 같은 행을 동시에 잡으면 늦게 커밋하는 쪽이 `ObjectOptimisticLockingFailureException`(500,
 * `GlobalExceptionHandler`가 잡는 미분류 예외)으로 죽는다. 프론트는 그 실패를 "세션 없음"으로
 * 해석해 방금 다른(취소되지 않은) 이펙트가 정상 발급한 세션까지 지워버린다 — 화면 로직과 무관한
 * 순수 부트스트랩 레이스이고, StrictMode가 프로덕션 빌드에는 없는 개발 전용 이중 호출이라
 * 실사용자 단일 새로고침에서는 트리거되지 않는다(다만 백엔드의 동시 refresh 미대응 자체는
 * 실제 결함이다 — 여러 탭·네트워크 재시도 등 다른 경로로도 트리거될 수 있다).
 *
 * 그 트리거(동시 이중 호출)를 브라우저에 도달하기 전에 라우트 레벨에서 한 건으로 합쳐서
 * 없앤다 — 실제로 서버에 나가는 `/auth/refresh`는 항상 1건이고, 두 프론트 호출 모두 같은
 * 성공 응답을 받는다. 애플리케이션 코드(프론트·백엔드)는 전혀 건드리지 않는다.
 */
export async function installAuthRefreshDedup(page: Page): Promise<void> {
  let inFlight: Promise<{ status: number; headers: Record<string, string>; body: Buffer }> | null =
    null;

  await page.route('**/api/v1/auth/refresh', async (route) => {
    if (!inFlight) {
      inFlight = (async () => {
        const response = await route.fetch();
        return { status: response.status(), headers: response.headers(), body: await response.body() };
      })();
    }
    const result = await inFlight;
    inFlight = null;
    await route.fulfill(result);
  });
}

/**
 * 시드 계정(Task 4-15, `V64__seed_member.sql`)으로 로그인 화면을 거쳐 로그인한다.
 * 로그인 성공 시 `/main`으로 이동하는 것(Login.tsx 주석 참고)을 완료 신호로 쓴다 —
 * 리프레시 토큰은 httpOnly 쿠키로 내려오므로 이후 페이지 이동에서도 세션이 유지된다.
 *
 * 첫 호출에서 `installAuthRefreshDedup`을 함께 걸어, 이 페이지가 겪을 모든 하드 네비게이션에
 * 대해 위 레이스 방지가 적용되게 한다(라우트 핸들러는 페이지 생애주기 동안 유지된다).
 */
export async function loginAsSeedUser(page: Page, email: string, password: string): Promise<void> {
  await installAuthRefreshDedup(page);
  await page.goto('/login');
  await page.getByLabel('이메일').fill(email);
  await page.getByLabel('비밀번호').fill(password);
  await page.getByRole('button', { name: '로그인' }).click();
  await expect(page).toHaveURL(/\/main/);
}

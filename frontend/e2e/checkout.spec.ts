import { expect, test, type Page } from '@playwright/test';
import { loginAsSeedUser } from './fixtures/auth';

const SEED_EMAIL = 'dry@beautyboy.dev';
const SEED_PASSWORD = 'seed1234!';

/**
 * 인증이 필요한 경로로 하드 네비게이션(`page.goto`)한다.
 *
 * 하드 네비게이션은 매번 App 부트스트랩(`POST /auth/refresh`)을 다시 태우고, `npm run dev`의
 * React StrictMode는 그 이펙트를 두 번 실행한다 — 즉 여기가 **동시 refresh를 실제로 발생시키는
 * 지점**이다. Task 4-16a 이전에는 그 레이스가 500 → 세션 소멸 → `/login` 튕김으로 이어졌고,
 * 픽스처의 `installAuthRefreshDedup` shim이 그것을 우회해 결함을 가리고 있었다.
 * shim과 함께 "튕겼으면 재로그인" 폴백도 없앴다 — 폴백이 남아 있으면 회귀가 조용히 통과한다.
 *
 * 그래서 여기서 로그인 화면으로 튕기지 않았음을 **단언**한다. 이것이 이 태스크가 지켜야 할
 * 불변식("유효한 리프레시 쿠키로 인증 경로에 하드 네비게이션하면 로그인 화면으로 튕기지 않는다")
 * 을 E2E에서 확인하는 자리다.
 */
async function gotoAuthenticated(page: Page, path: string): Promise<void> {
  await page.goto(path);
  await page.waitForLoadState('networkidle');
  await expect(page).not.toHaveURL(/\/login/);
}

/**
 * 루틴 가이드 → 전체 담기 → 장바구니 → 주문서 → 결제하기까지 화면으로 진행하고,
 * 서버가 실제로 만든 주문(`POST /orders` 응답)의 orderNo·payableAmount를 네트워크 응답에서
 * 그대로 읽어 돌려준다. 결정 5에 따라 그 다음(토스 결제창)은 자동화하지 않는다 — 여기서 멈춘다.
 *
 * 장바구니 한 줄 확인은 `data-testid="cart-line"`을 새로 뿌리는 대신 접근성 역할로 잡는다.
 * `CartLine`이 렌더하는 `<article>`은 HTML5 표준으로 암묵적 role="article"을 갖고, 라인 개수만큼
 * 하나씩 나오므로 역할 카운트만으로 브리프가 요구하는 "장바구니에 5줄" 단언을 그대로 만족한다
 * (판단 근거는 보고서 참고).
 */
async function addRoutineToCartAndCreateOrder(
  page: Page,
): Promise<{ orderNo: string; payableAmount: number }> {
  await gotoAuthenticated(page, '/routine');
  await expect(page.getByTestId('routine-step')).toHaveCount(5);
  await page.getByRole('button', { name: '루틴 전체 담기' }).click();

  await expect(page).toHaveURL(/\/cart/);
  await expect(page.getByRole('article')).toHaveCount(5);

  await page.getByRole('button', { name: '주문하기' }).click();
  await expect(page).toHaveURL(/\/order/);

  const [orderResponse] = await Promise.all([
    page.waitForResponse(
      (res) => res.request().method() === 'POST' && res.url().includes('/api/v1/orders'),
    ),
    page.getByRole('button', { name: '결제하기' }).click(),
  ]);
  const body = await orderResponse.json();
  return { orderNo: body.data.orderNo as string, payableAmount: body.data.payableAmount as number };
}

test('탐색 → 루틴 전체 담기 → 장바구니 → 주문 → 결제 승인 → 완료', async ({ page }) => {
  await loginAsSeedUser(page, SEED_EMAIL, SEED_PASSWORD);

  const { orderNo, payableAmount } = await addRoutineToCartAndCreateOrder(page);

  // 토스 결제창은 자동화하지 않는다(결정 5) — 토스가 성공 시 보내는 리다이렉트를 그대로 재현한다.
  // 승인 검증은 진짜 PaymentService가 한다 — 가짜인 것은 게이트웨이 네트워크 호출뿐이다(e2e 프로필).
  await gotoAuthenticated(
    page,
    `/order/complete?paymentKey=pk_e2e_${orderNo}&orderId=${orderNo}&amount=${payableAmount}`,
  );

  await expect(page.getByText('주문이 완료되었습니다')).toBeVisible();
  await expect(page.getByText(orderNo)).toBeVisible();

  // 마이페이지 주문내역에 남는다. 목록 화면(`/mypage/orders`)은 날짜·상품명·금액만 보여주고
  // 주문번호를 표시하지 않는다(MyOrders.tsx OrderListView) — 상세(`/mypage/orders/:orderNo`)에만
  // "주문번호 {orderNo}"가 나온다. 같은 컴포넌트가 라우트 파라미터로 목록/상세를 가르므로 상세로
  // 바로 이동해 주문내역에 남았는지 확인한다(실제 UI 갭은 보고서에 기록).
  await gotoAuthenticated(page, `/mypage/orders/${orderNo}`);
  await expect(page.getByText(orderNo)).toBeVisible();
});

test('금액을 위조한 승인 요청은 거부되고 완료로 표시되지 않는다', async ({ page }) => {
  // 결제 2단계 검증이 화면까지 이어지는지 — 이 프로젝트에서 가장 설명할 값어치가 있는 경로다.
  await loginAsSeedUser(page, SEED_EMAIL, SEED_PASSWORD);
  const { orderNo } = await addRoutineToCartAndCreateOrder(page);

  await gotoAuthenticated(page, `/order/complete?paymentKey=pk_e2e&orderId=${orderNo}&amount=100`);

  await expect(page.getByRole('alert')).toContainText('일치하지 않');
  await expect(page.getByText('주문이 완료되었습니다')).toHaveCount(0);
});

test('비로그인으로 보호 화면에 가면 로그인으로 보낸다', async ({ page }) => {
  await page.goto('/cart');
  await expect(page).toHaveURL(/\/login/);
});

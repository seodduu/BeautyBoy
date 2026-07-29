import { expect, type APIRequestContext, type Page } from '@playwright/test';

/**
 * 취소 여정이 재사용하는 "결제까지 끝난 주문" 헬퍼.
 *
 * checkout.spec.ts와 같은 경로를 걷지만(루틴 담기 → 주문 → 승인 리다이렉트), 취소 스펙은
 * 그 위에 두 가지를 더 요구한다: **수량 2짜리 줄**(부분취소를 볼 수 있는 유일한 조건)과
 * **주문에 들어간 옵션 정보**(재고 복원을 옵션 재고로 확인하려면 optionNo가 필요하다).
 * 그래서 체크아웃 헬퍼를 그대로 부르지 않고 여기에 취소 전용으로 한 벌 둔다.
 */

/** 주문에 들어간 줄 하나 — 장바구니 응답을 그대로 옮긴 것이다(재고 대조용 optionNo가 핵심). */
export interface OrderedLine {
  goodsNo: number;
  optionNo: number | null;
  goodsName: string;
  quantity: number;
  unitPrice: number;
}

export interface PaidOrder {
  orderNo: string;
  payableAmount: number;
  lines: OrderedLine[];
}

/**
 * 인증이 필요한 경로로 하드 네비게이션한다(checkout.spec.ts와 같은 규약).
 * 로그인 화면으로 튕기지 않았음을 단언해, 세션 유지 실패를 취소 실패로 오인하지 않게 한다.
 */
export async function gotoAuthenticated(page: Page, path: string): Promise<void> {
  await page.goto(path);
  await page.waitForLoadState('networkidle');
  await expect(page).not.toHaveURL(/\/login/);
}

/**
 * 시드 계정 장바구니를 비운다.
 *
 * 루틴 담기 뒤의 "5줄" 단언은 장바구니가 비어 있다는 가정 위에 서 있다. 수동 검증으로 담아둔
 * 잔여물이 남아 있으면 취소 로직과 무관한 자리에서 빨간불이 뜬다 — 그 거짓 신호를 여기서 없앤다.
 */
export async function clearCart(page: Page): Promise<void> {
  await gotoAuthenticated(page, '/cart');
  // 삭제는 한 줄씩 무효화를 태우므로, 매번 첫 줄을 지우고 목록이 줄어드는 것을 기다린다.
  for (let remaining = await page.getByRole('article').count(); remaining > 0; remaining -= 1) {
    await page.getByRole('button', { name: '삭제' }).first().click();
    await expect(page.getByRole('article')).toHaveCount(remaining - 1);
  }
}

/**
 * 루틴 전체 담기 → (첫 줄 수량 2로) → 주문 → 토스 성공 리다이렉트 재현까지 진행해
 * **PAID 주문**을 만든다. 결제창 자체는 자동화하지 않는다(checkout.spec.ts 결정 5와 동일) —
 * 승인 검증은 e2e 프로필의 진짜 PaymentService가 수행하고, 가짜인 것은 게이트웨이 호출뿐이다.
 */
export async function createPaidOrder(page: Page): Promise<PaidOrder> {
  await clearCart(page);

  await gotoAuthenticated(page, '/routine');
  await expect(page.getByTestId('routine-step')).toHaveCount(5);
  await page.getByRole('button', { name: '루틴 전체 담기' }).click();

  await expect(page).toHaveURL(/\/cart/);
  await expect(page.getByRole('article')).toHaveCount(5);

  // 첫 줄만 2개로 올린다 — "2개 중 1개 취소"가 성립하려면 수량 2짜리 줄이 하나는 있어야 한다.
  const firstLine = page.getByRole('article').first();
  await Promise.all([
    page.waitForResponse(
      (res) => res.request().method() === 'PATCH' && res.url().includes('/api/v1/cart/items/'),
    ),
    firstLine.getByRole('button', { name: '수량 늘리기' }).click(),
  ]);
  await expect(firstLine.getByRole('status')).toHaveText('2');

  // 주문에 무엇이 담겼는지는 화면 텍스트가 아니라 서버 응답에서 읽는다 — 재고 대조에 쓸
  // optionNo는 화면에 나오지 않기 때문이다.
  const [cartResponse] = await Promise.all([
    page.waitForResponse(
      (res) => res.request().method() === 'GET' && res.url().includes('/api/v1/cart/items'),
    ),
    page.goto('/cart'),
  ]);
  const lines: OrderedLine[] = (await cartResponse.json()).data.map(
    (item: {
      goodsNo: number;
      optionNo: number | null;
      goodsName: string;
      quantity: number;
      unitPrice: number;
    }) => ({
      goodsNo: item.goodsNo,
      optionNo: item.optionNo,
      goodsName: item.goodsName,
      quantity: item.quantity,
      unitPrice: item.unitPrice,
    }),
  );

  await page.getByRole('button', { name: '주문하기' }).click();
  await expect(page).toHaveURL(/\/order/);

  const [orderResponse] = await Promise.all([
    page.waitForResponse(
      (res) => res.request().method() === 'POST' && res.url().includes('/api/v1/orders'),
    ),
    page.getByRole('button', { name: '결제하기' }).click(),
  ]);
  const created = (await orderResponse.json()).data;
  const orderNo = created.orderNo as string;
  const payableAmount = created.payableAmount as number;

  await gotoAuthenticated(
    page,
    `/order/complete?paymentKey=pk_e2e_${orderNo}&orderId=${orderNo}&amount=${payableAmount}`,
  );
  await expect(page.getByText('주문이 완료되었습니다')).toBeVisible();

  return { orderNo, payableAmount, lines };
}

/**
 * 상품 상세의 옵션 재고를 읽는다. 재고는 화면에 숫자로 노출되지 않으므로(수량 상한으로만
 * 쓰인다) 공개 조회 API를 그대로 부른다 — 취소 전후 차이를 재는 데 이것 말고는 자리가 없다.
 */
export async function readOptionStock(
  request: APIRequestContext,
  goodsNo: number,
  optionNo: number,
): Promise<number> {
  const response = await request.get(`/api/v1/goods/${goodsNo}`);
  expect(response.ok()).toBeTruthy();
  const options: { optionNo: number; stock: number }[] = (await response.json()).data.options;
  const option = options.find((o) => o.optionNo === optionNo);
  if (!option) {
    throw new Error(`상품 ${goodsNo}에 옵션 ${optionNo}이 없다 — 시드가 바뀌었는지 확인한다`);
  }
  return option.stock;
}

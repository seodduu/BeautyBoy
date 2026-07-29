import { expect, test, type Page, type Response } from '@playwright/test';
import { loginAsSeedUser } from './fixtures/auth';
import {
  createPaidOrder,
  gotoAuthenticated,
  readOptionStock,
  type OrderedLine,
} from './fixtures/order';

const SEED_EMAIL = 'dry@beautyboy.dev';
const SEED_PASSWORD = 'seed1234!';

/** 결제까지 끝난 주문 하나를 만드는 데만 화면 여러 장을 지난다 — 기본 30초로는 모자란다. */
const CANCEL_JOURNEY_TIMEOUT_MS = 120_000;

/** 취소 모달에서 한 상품 줄을 집는다. 모달과 상세 화면에 같은 상품명이 있으므로 dialog 안에서 찾는다. */
function modalItem(page: Page, goodsName: string) {
  return page.getByRole('dialog').getByRole('listitem').filter({ hasText: goodsName });
}

/**
 * 취소 모달을 열어 지정한 줄들을 고르고 확정한다. 서버가 확정한 환불액(응답의 refundAmount)을
 * 돌려준다 — 화면의 "예상 환불액"은 어디까지나 예상치라 단언 근거로 쓰지 않는다.
 */
async function cancelItems(
  page: Page,
  picks: { goodsName: string; quantity: number }[],
): Promise<{ refundAmount: number; status: string }> {
  await page.getByRole('button', { name: '주문 취소' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();

  for (const pick of picks) {
    const item = modalItem(page, pick.goodsName);
    await item.getByRole('checkbox').check();
    // 체크하면 1개로 시작한다 — 필요한 만큼만 올린다(상한은 잔여 수량이라 넘길 수 없다).
    for (let q = 1; q < pick.quantity; q += 1) {
      await item.getByRole('button', { name: '수량 늘리기' }).click();
    }
    await expect(item.getByRole('status')).toHaveText(String(pick.quantity));
  }

  const [cancelResponse] = await Promise.all([
    page.waitForResponse(
      (res) => res.request().method() === 'POST' && res.url().includes('/cancel'),
    ),
    dialog.getByRole('button', { name: '취소 확정' }).click(),
  ]);
  const body = (await cancelResponse.json()).data;

  // 성공하면 모달이 닫히고 상세·목록 쿼리가 무효화된다. 그 재조회가 끝나기 전에 다음 이동을
  // 시작하면, 아직 오지 않은 응답이 다음 단계의 대기와 엉켜 본문을 읽지 못한다 —
  // 여기서 화면이 스스로 다시 조회하는 것까지 기다려 그 레이스를 없앤다.
  await expect(dialog).toHaveCount(0);
  await page.waitForLoadState('networkidle');

  return { refundAmount: body.refundAmount as number, status: body.status as string };
}

/**
 * 상세 화면을 다시 열면서 그때 서버가 내려준 주문 상세를 그대로 읽는다(화면 단언의 뒷받침).
 *
 * 응답 본문은 **도착하는 즉시** 읽기 시작한다. 취소 직후에는 무효화로 뜬 재조회가 아직
 * 날아다닐 수 있고, 그 응답은 이어지는 하드 네비게이션이 폐기해버려 나중에 읽으면
 * "navigated away"로 실패한다. 그래서 후보를 모두 받아두고 살아남은 마지막 것을 쓴다 —
 * 마지막이 곧 이 화면이 실제로 렌더한 상세다.
 */
async function reloadDetail(
  page: Page,
  orderNo: string,
): Promise<{ status: string; items: { goodsName: string; canceledQuantity: number }[] }> {
  const bodies: Promise<{ data?: unknown } | null>[] = [];
  // `/api/v1/`까지 붙여 판별한다 — 상세 화면 주소 자체가 `/mypage/orders/{orderNo}`라
  // 경로만 보면 문서 응답(HTML)이 먼저 걸리고, 그건 JSON이 아니다.
  const collect = (res: Response) => {
    if (res.request().method() === 'GET' && res.url().includes(`/api/v1/orders/${orderNo}`)) {
      bodies.push(res.json().catch(() => null));
    }
  };

  page.on('response', collect);
  try {
    await gotoAuthenticated(page, `/mypage/orders/${orderNo}`);
    await expect(page.getByText(`주문번호 ${orderNo}`)).toBeVisible();
  } finally {
    page.off('response', collect);
  }

  const alive = (await Promise.all(bodies)).filter(
    (body): body is { data: unknown } => body !== null && 'data' in body,
  );
  if (alive.length === 0) {
    throw new Error(`주문 ${orderNo} 상세 응답을 하나도 읽지 못했다`);
  }
  return alive[alive.length - 1].data as {
    status: string;
    items: { goodsName: string; canceledQuantity: number }[];
  };
}

/** 수량 2로 올려 담은 줄 — 부분취소를 볼 수 있는 유일한 줄이다. */
function twoQuantityLine(lines: OrderedLine[]): OrderedLine {
  const line = lines.find((l) => l.quantity === 2);
  if (!line) {
    throw new Error('수량 2짜리 줄이 없다 — createPaidOrder의 수량 올리기가 먹지 않았다');
  }
  return line;
}

test('수량_부분취소하면_배지가_부분취소로_바뀌고_환불액이_표시된다', async ({ page }) => {
  test.setTimeout(CANCEL_JOURNEY_TIMEOUT_MS);
  await loginAsSeedUser(page, SEED_EMAIL, SEED_PASSWORD);
  const order = await createPaidOrder(page);
  const target = twoQuantityLine(order.lines);

  await gotoAuthenticated(page, `/mypage/orders/${order.orderNo}`);
  const { refundAmount } = await cancelItems(page, [
    { goodsName: target.goodsName, quantity: 1 },
  ]);

  // 환불액은 주문 시점 스냅샷 단가로 서버가 확정한다 — 1개분이어야 한다.
  expect(refundAmount).toBe(target.unitPrice);

  const detail = await reloadDetail(page, order.orderNo);
  expect(detail.status).toBe('PARTIALLY_CANCELED');
  expect(detail.items.find((i) => i.goodsName === target.goodsName)?.canceledQuantity).toBe(1);

  await expect(page.getByText('부분취소')).toBeVisible();
  // 이력은 회차 단위로 남고 금액 앞에 −가 붙는다(MyOrders 취소 내역).
  await expect(page.getByText('취소 내역')).toBeVisible();
  await expect(page.getByText(`−${refundAmount.toLocaleString('ko-KR')}원`)).toBeVisible();
  // 잔여가 남았으므로 취소 버튼은 그대로 있어야 한다.
  await expect(page.getByRole('button', { name: '주문 취소' })).toBeVisible();
});

test('남은_전량을_취소하면_취소완료가_된다', async ({ page }) => {
  test.setTimeout(CANCEL_JOURNEY_TIMEOUT_MS);
  await loginAsSeedUser(page, SEED_EMAIL, SEED_PASSWORD);
  const order = await createPaidOrder(page);
  const target = twoQuantityLine(order.lines);

  await gotoAuthenticated(page, `/mypage/orders/${order.orderNo}`);
  await cancelItems(page, [{ goodsName: target.goodsName, quantity: 1 }]);
  await reloadDetail(page, order.orderNo);

  // 이어서 잔여 전량 — 수량 2짜리 줄의 남은 1개와 나머지 줄 전부.
  const rest = order.lines.map((line) => ({
    goodsName: line.goodsName,
    quantity: line.goodsName === target.goodsName ? line.quantity - 1 : line.quantity,
  }));
  const { status } = await cancelItems(page, rest);
  expect(status).toBe('CANCELED');

  const detail = await reloadDetail(page, order.orderNo);
  expect(detail.status).toBe('CANCELED');
  expect(detail.items.every((i) => i.canceledQuantity > 0)).toBe(true);

  await expect(page.getByText('취소완료')).toBeVisible();
  // 더 취소할 것이 없으면 버튼 자체가 사라진다(isCancelable이 CANCELED를 제외한다).
  await expect(page.getByRole('button', { name: '주문 취소' })).toHaveCount(0);
});

test('취소후_재고가_복원된다', async ({ page }) => {
  test.setTimeout(CANCEL_JOURNEY_TIMEOUT_MS);
  await loginAsSeedUser(page, SEED_EMAIL, SEED_PASSWORD);
  const order = await createPaidOrder(page);
  const target = twoQuantityLine(order.lines);
  if (target.optionNo === null) {
    throw new Error('옵션 없는 상품은 재고를 관리하지 않는다 — 시드 루틴 구성이 바뀌었다');
  }

  // 결제 승인이 이미 재고를 깎은 뒤의 값이 기준선이다.
  const before = await readOptionStock(page.request, target.goodsNo, target.optionNo);

  await gotoAuthenticated(page, `/mypage/orders/${order.orderNo}`);
  await cancelItems(page, [{ goodsName: target.goodsName, quantity: 1 }]);
  await reloadDetail(page, order.orderNo);

  const after = await readOptionStock(page.request, target.goodsNo, target.optionNo);
  expect(after).toBe(before + 1);
});

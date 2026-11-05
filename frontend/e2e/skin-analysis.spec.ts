import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';
import { loginAsSeedUser } from './fixtures/auth';

// 가짜 카메라 영상. 없으면 skip 하지 않고 실패한다(거짓 녹색 방지). 만들기: sh e2e/fixtures/skin/make-fixture.sh
const Y4M = fileURLToPath(new URL('./fixtures/skin/portrait.y4m', import.meta.url));
if (!existsSync(Y4M)) {
  throw new Error('make-fixture.sh 를 먼저 실행: sh e2e/fixtures/skin/make-fixture.sh');
}

// 제안 조건(OILY, soothe 없음)을 만족하는 시드 계정. dry 는 공유 DB 에서 SENSITIVE 로 바뀌어 있어 제안이 나오지 않는다.
const SEED_EMAIL = 'oily@beautyboy.dev';
const SEED_PASSWORD = 'seed1234!';
const HISTORY_PREFIX = 'bb.skin.v1.';

/** 모델(약 18MB) 첫 로드가 들어가므로 기본 30초로는 모자란다. */
const SKIN_JOURNEY_TIMEOUT_MS = 120_000;

interface MeResponse {
  id: number;
  skinType: string | null;
  concerns: string[];
  ageBand: string | null;
}

interface StoredRecord {
  redness: number;
  foreheadL: number;
  [k: string]: unknown;
}

test.use({
  launchOptions: {
    args: [
      '--use-fake-device-for-media-stream',
      '--use-fake-ui-for-media-stream',
      `--use-file-for-fake-video-capture=${Y4M}`,
    ],
  },
});

test('첫_측정은_기준값을_기록하고_기준을_낮추면_증가와_제안_PUT이_간다', async ({ page }) => {
  test.setTimeout(SKIN_JOURNEY_TIMEOUT_MS);

  // 공유 시드 계정의 프로필에 실제로 쓰지 않는다. PUT 만 가로채 바디를 잡는다.
  let captured: Record<string, unknown> | null = null;
  await page.route('**/api/v1/members/me/profile', (route) => {
    if (route.request().method() === 'PUT') {
      captured = route.request().postDataJSON() as Record<string, unknown>;
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'OK', message: 'success', data: null }),
      });
    }
    return route.continue();
  });

  await loginAsSeedUser(page, SEED_EMAIL, SEED_PASSWORD);
  await page.evaluate((prefix) => {
    Object.keys(localStorage)
      .filter((k) => k.startsWith(prefix))
      .forEach((k) => localStorage.removeItem(k));
  }, HISTORY_PREFIX);

  // 실 DB 값은 다른 작업으로 바뀌었을 수 있으므로 리터럴 대신 화면이 받은 응답을 기준으로 단언한다.
  const meResponse = page.waitForResponse(
    (r) => r.url().endsWith('/api/v1/members/me') && r.request().method() === 'GET' && r.ok(),
  );
  await page.goto('/skin-analysis');
  const me = ((await (await meResponse).json()) as { data: MeResponse }).data;
  if (me.skinType === 'SENSITIVE' || me.concerns.includes('soothe')) {
    throw new Error(
      `시드 계정 상태가 제안 조건을 만족하지 않는다: skinType=${me.skinType}, concerns=${me.concerns.join(',')}`,
    );
  }

  const captureButton = page.getByRole('button', { name: '촬영' });

  // 1) 첫 측정: 기준값 기록, 이력 1건
  await expect(captureButton).toBeEnabled({ timeout: 60_000 });
  await captureButton.click();
  await expect(page.getByText('기준값을 기록했어요')).toBeVisible({ timeout: 30_000 });

  const historyKey = `${HISTORY_PREFIX}${me.id}`;
  const first = await page.evaluate((key) => JSON.parse(localStorage.getItem(key) ?? '[]') as StoredRecord[], historyKey);
  expect(first).toHaveLength(1);

  // 2) 기준 레코드의 redness 만 1.0 낮춰 덮어쓰고 다시 측정하면 "늘었어요" 와 제안이 나온다
  await page.evaluate(
    ([key, record]) => localStorage.setItem(key, JSON.stringify([record])),
    [historyKey, { ...first[0], redness: first[0].redness - 1.0 }] as const,
  );
  await page.reload();
  await expect(captureButton).toBeEnabled({ timeout: 60_000 });
  await captureButton.click();
  await expect(page.getByText(/늘었/)).toBeVisible({ timeout: 30_000 });

  await page.getByRole('button', { name: "프로필 고민에 '진정' 추가" }).click();
  await expect(page.getByText("프로필 고민에 '진정'을 추가했어요")).toBeVisible();

  // 서버가 세 필드를 통째로 대체하므로 최신 me 의 값이 전부 실려야 한다
  expect(captured).not.toBeNull();
  const body = captured as unknown as Record<string, unknown>;
  expect(body.concerns).toEqual([...me.concerns, 'soothe']);
  expect(body.skinType).toBe(me.skinType ?? undefined);
  if (me.ageBand === null) expect(body).not.toHaveProperty('ageBand');
  else expect(body.ageBand).toBe(me.ageBand);
});

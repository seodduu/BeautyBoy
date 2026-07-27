import { beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import type { FlowRulesResponse } from '../../types/routine';
import { EMPTY_RULES, loadFlowRules } from './flowRules';

const STORAGE_KEY = 'bb.flowRules.v1';

const rulesV1: FlowRulesResponse = {
  version: 'v1',
  flowRules: [
    {
      fromCategoryCode: 'C001001',
      fromTagSlug: null,
      toCategoryCode: 'C001002',
      toTagSlug: 'moisture',
      edgeKind: 'NEXT_STEP',
      reason: '결을 정돈했다면 영양을 채울 차례예요',
      priority: 20,
    },
  ],
  concernRules: [
    {
      concernTagSlug: 'moisture',
      toCategoryCode: 'C001003',
      toTagSlug: 'moisture',
      reason: '보습이 고민이라면 덮어 가두는 크림이 핵심이에요',
      priority: 10,
    },
  ],
};

/** 요청마다 실제로 실려 온 If-None-Match를 남겨 두고 응답을 고른다. */
function serveRules(body: FlowRulesResponse, seen: string[]) {
  server.use(
    http.get('/api/v1/routine/flow-rules', ({ request }) => {
      const ifNoneMatch = request.headers.get('If-None-Match');
      seen.push(ifNoneMatch ?? '(없음)');
      if (ifNoneMatch === body.version) {
        return new HttpResponse(null, { status: 304 });
      }
      return HttpResponse.json({ code: 'OK', message: 'success', data: body });
    }),
  );
}

beforeEach(() => {
  localStorage.clear();
});

describe('loadFlowRules — ETag 캐시', () => {
  it('저장본이 없으면 If-None-Match 없이 받아 그대로 돌려준다', async () => {
    const seen: string[] = [];
    serveRules(rulesV1, seen);

    expect(await loadFlowRules()).toEqual(rulesV1);
    expect(seen).toEqual(['(없음)']);
  });

  it('받은 규칙을 localStorage에 저장한다', async () => {
    serveRules(rulesV1, []);

    await loadFlowRules();

    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual(rulesV1);
  });

  it('두 번째 호출은 저장된 version을 If-None-Match로 보내고, 304면 저장본을 그대로 쓴다', async () => {
    const seen: string[] = [];
    serveRules(rulesV1, seen);

    await loadFlowRules();
    const second = await loadFlowRules();

    expect(seen).toEqual(['(없음)', 'v1']);
    expect(second).toEqual(rulesV1);
  });

  it('서버 version이 바뀌면 새 규칙으로 저장본을 갈아치운다', async () => {
    serveRules(rulesV1, []);
    await loadFlowRules();

    const rulesV2: FlowRulesResponse = { ...rulesV1, version: 'v2', concernRules: [] };
    serveRules(rulesV2, []);

    expect(await loadFlowRules()).toEqual(rulesV2);
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual(rulesV2);
  });

  it('네트워크가 실패해도 저장본이 있으면 그것을 쓴다', async () => {
    serveRules(rulesV1, []);
    await loadFlowRules();

    server.use(http.get('/api/v1/routine/flow-rules', () => HttpResponse.error()));

    expect(await loadFlowRules()).toEqual(rulesV1);
  });

  it('네트워크가 실패하고 저장본도 없으면 빈 규칙으로 간다 — 티어0 폴백', async () => {
    server.use(http.get('/api/v1/routine/flow-rules', () => HttpResponse.error()));

    expect(await loadFlowRules()).toEqual(EMPTY_RULES);
  });

  it('서버가 500을 내도 던지지 않는다 — 규칙을 못 받았다고 메인이 깨지면 안 된다', async () => {
    server.use(
      http.get('/api/v1/routine/flow-rules', () => new HttpResponse(null, { status: 500 })),
    );

    await expect(loadFlowRules()).resolves.toEqual(EMPTY_RULES);
  });

  it('저장본이 손상됐으면 폐기하고 새로 받는다', async () => {
    localStorage.setItem(STORAGE_KEY, '{깨진 값');
    const seen: string[] = [];
    serveRules(rulesV1, seen);

    expect(await loadFlowRules()).toEqual(rulesV1);
    expect(seen).toEqual(['(없음)']);
  });

  it('저장본 형태가 계약과 어긋나면 폐기한다', async () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ version: 'v1', flowRules: '배열이 아님' }));
    const seen: string[] = [];
    serveRules(rulesV1, seen);

    expect(await loadFlowRules()).toEqual(rulesV1);
    expect(seen).toEqual(['(없음)']);
  });

  it('저장본이 있는데 304를 못 받고 요청이 실패하면 저장본으로 버틴다', async () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(rulesV1));

    server.use(
      http.get('/api/v1/routine/flow-rules', () => new HttpResponse(null, { status: 503 })),
    );

    expect(await loadFlowRules()).toEqual(rulesV1);
  });
});

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { FaceLandmarkDetector } from './types';

const mp = vi.hoisted(() => {
  const state: { resolve: (v: unknown) => void; promise: Promise<unknown> } = {
    resolve: () => {},
    promise: Promise.resolve(),
  };
  return {
    state,
    /** createFromOptions 가 돌려줄 promise 를 새 deferred 로 바꾼다. */
    newDeferred() {
      state.promise = new Promise((res) => { state.resolve = res; });
    },
    createFromOptions: vi.fn(() => state.promise),
    forVisionTasks: vi.fn().mockResolvedValue({}),
  };
});

vi.mock('@mediapipe/tasks-vision', () => ({
  FilesetResolver: { forVisionTasks: mp.forVisionTasks },
  FaceLandmarker: { createFromOptions: mp.createFromOptions },
}));

function fakeLandmarker() {
  return { detect: vi.fn(() => ({ faceLandmarks: [] })), close: vi.fn() };
}

async function load() {
  return (await import('./landmarker')).createMediaPipeDetector;
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.resetModules();
  mp.newDeferred();
});
afterEach(() => {
  vi.useRealTimers();
  // 테스트가 바꾼 mock 구현을 되돌린다(clearAllMocks 는 구현을 지우지 않는다).
  mp.forVisionTasks.mockResolvedValue({});
  mp.createFromOptions.mockImplementation(() => mp.state.promise);
});

describe('createMediaPipeDetector', () => {
  it('createMediaPipeDetector는_함수다', async () => {
    expect(typeof (await load())).toBe('function');
  });

  it('생성_중_재호출은_같은_생성을_넘겨받는다', async () => {
    // 30초 창이 만료되기 전의 재호출이다. 만료 뒤의 재호출은 아래 '생성이_멈추면' 테스트가 본다(설계 6.2 정정).
    vi.useFakeTimers();
    const createMediaPipeDetector = await load();
    const lm = fakeLandmarker();

    const p1 = createMediaPipeDetector();
    await vi.advanceTimersByTimeAsync(10_000);
    expect(mp.createFromOptions).toHaveBeenCalledTimes(1);

    const p2 = createMediaPipeDetector();
    expect(mp.createFromOptions).toHaveBeenCalledTimes(1);

    mp.state.resolve(lm);
    const detector: FaceLandmarkDetector = await p2;
    expect(detector).toBeDefined();
    expect(await p1).toBe(detector);
    expect(mp.createFromOptions).toHaveBeenCalledTimes(1);
    expect(lm.close).not.toHaveBeenCalled();
  });

  it('생성이_멈추면_재시도는_새로_생성한다', async () => {
    vi.useFakeTimers();
    // 첫 생성은 끝나지 않는다(reject 도 resolve 도 없음).
    mp.forVisionTasks.mockReturnValueOnce(new Promise(() => {}));
    const createMediaPipeDetector = await load();
    const lm = fakeLandmarker();
    mp.state.resolve(lm);

    const p1 = createMediaPipeDetector();
    const check = expect(p1).rejects.toThrow('model-load-failed');
    await vi.advanceTimersByTimeAsync(31_000);
    await check;

    const detector = await createMediaPipeDetector();
    expect(mp.forVisionTasks).toHaveBeenCalledTimes(2);
    expect(detector).toBeDefined();
  });

  it('느린_옛_생성이_먼저_끝나면_재시도가_그것을_받는다', async () => {
    // 생성 한 번에 40초가 걸린다. 31초에 창이 만료돼 재시도가 새 생성을 시작해도, 40초에 끝나는 옛 생성을 받아야 한다(재검토 N1).
    vi.useFakeTimers();
    const t0 = Date.now();
    mp.forVisionTasks.mockImplementation(() => new Promise((res) => setTimeout(() => res({}), 40_000)));
    const made: ReturnType<typeof fakeLandmarker>[] = [];
    mp.createFromOptions.mockImplementation(() => {
      const lm = fakeLandmarker();
      made.push(lm);
      return Promise.resolve(lm);
    });
    const createMediaPipeDetector = await load();

    const p1 = createMediaPipeDetector();
    const check = expect(p1).rejects.toThrow('model-load-failed');
    await vi.advanceTimersByTimeAsync(31_000);
    await check;

    let resolvedAt = -1;
    const p2 = createMediaPipeDetector().then((d) => {
      resolvedAt = Date.now() - t0;
      return d;
    });
    await vi.advanceTimersByTimeAsync(9_000);   // t = 40초: 옛 생성 완료
    const detector = await p2;
    expect(resolvedAt).toBe(40_000);
    expect(mp.forVisionTasks).toHaveBeenCalledTimes(2);
    expect(made).toHaveLength(1);
    expect(made[0].close).not.toHaveBeenCalled();

    // 새 생성은 71초에 끝나고, 캐시가 이미 있으므로 기존 규칙대로 닫힌다. 캐시는 그대로다.
    await vi.advanceTimersByTimeAsync(31_000);
    expect(made).toHaveLength(2);
    expect(made[1].close).toHaveBeenCalledTimes(1);
    expect(made[0].close).not.toHaveBeenCalled();
    expect(await createMediaPipeDetector()).toBe(detector);
  });

  it('버린_생성이_늦게_완성되면_캐시가_있으면_닫는다', async () => {
    vi.useFakeTimers();
    let resolveFirst: (v: unknown) => void = () => {};
    const late = fakeLandmarker();
    const fresh = fakeLandmarker();
    mp.createFromOptions
      .mockImplementationOnce(() => new Promise((res) => { resolveFirst = res; }))
      .mockImplementationOnce(() => Promise.resolve(fresh));
    const createMediaPipeDetector = await load();

    const p1 = createMediaPipeDetector();
    const check = expect(p1).rejects.toThrow('model-load-failed');
    await vi.advanceTimersByTimeAsync(31_000);
    await check;

    const detector = await createMediaPipeDetector();
    expect(mp.createFromOptions).toHaveBeenCalledTimes(2);

    resolveFirst(late);
    await vi.advanceTimersByTimeAsync(0);
    expect(late.close).toHaveBeenCalledTimes(1);
    expect(fresh.close).not.toHaveBeenCalled();
    // 캐시는 새 검출기 그대로다
    expect(await createMediaPipeDetector()).toBe(detector);
  });

  it('언마운트_close는_캐시도_비운다', async () => {
    const createMediaPipeDetector = await load();
    const lm = fakeLandmarker();
    mp.state.resolve(lm);

    const detector = await createMediaPipeDetector();
    expect(mp.createFromOptions).toHaveBeenCalledTimes(1);
    // 캐시 히트: 새로 만들지 않는다
    await createMediaPipeDetector();
    expect(mp.createFromOptions).toHaveBeenCalledTimes(1);

    detector.close();
    expect(lm.close).toHaveBeenCalledTimes(1);

    await createMediaPipeDetector();
    expect(mp.createFromOptions).toHaveBeenCalledTimes(2);
  });

  it('detect는_얼굴이_없으면_landmarks_null이다', async () => {
    const createMediaPipeDetector = await load();
    const lm = fakeLandmarker();
    mp.state.resolve(lm);
    const detector = await createMediaPipeDetector();
    expect(detector.detect(document.createElement('canvas'))).toEqual({ landmarks: null });
  });
});

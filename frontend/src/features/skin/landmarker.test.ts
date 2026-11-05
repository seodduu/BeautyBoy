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
afterEach(() => vi.useRealTimers());

describe('createMediaPipeDetector', () => {
  it('createMediaPipeDetector는_함수다', async () => {
    expect(typeof (await load())).toBe('function');
  });

  it('생성_중_재호출은_같은_생성을_넘겨받는다', async () => {
    vi.useFakeTimers();
    const createMediaPipeDetector = await load();
    const lm = fakeLandmarker();

    const p1 = createMediaPipeDetector();
    const check = expect(p1).rejects.toThrow('model-load-failed');
    await vi.advanceTimersByTimeAsync(31_000);
    await check;
    expect(mp.createFromOptions).toHaveBeenCalledTimes(1);

    const p2 = createMediaPipeDetector();
    expect(mp.createFromOptions).toHaveBeenCalledTimes(1);

    mp.state.resolve(lm);
    const detector: FaceLandmarkDetector = await p2;
    expect(detector).toBeDefined();
    expect(mp.createFromOptions).toHaveBeenCalledTimes(1);
    expect(lm.close).not.toHaveBeenCalled();
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

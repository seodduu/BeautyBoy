import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { captureAndAnalyze } from './pipeline';
import type { FaceLandmarkDetector, NormalizedLandmark } from './types';

/** 같은 색 4픽셀짜리 RGBA. */
function solid(r: number, g: number, b: number): { data: Uint8ClampedArray } {
  const out: number[] = [];
  for (let i = 0; i < 4; i++) out.push(r, g, b, 255);
  return { data: new Uint8ClampedArray(out) };
}

/** 478점 모두 (0.5, 0.5). 얼굴 폭 0.3~0.7, 코끝 0.5 가 되도록 몇 점만 덮어쓴다. */
function faceLandmarks(): NormalizedLandmark[] {
  const lm: NormalizedLandmark[] = Array.from({ length: 478 }, () => ({ x: 0.5, y: 0.5 }));
  lm[400] = { x: 0.3, y: 0.5 };
  lm[401] = { x: 0.7, y: 0.5 };
  lm[234] = { x: 0.3, y: 0.5 };
  lm[454] = { x: 0.7, y: 0.5 };
  lm[1] = { x: 0.5, y: 0.5 };
  return lm;
}

const video = { videoWidth: 1000, videoHeight: 1000 } as HTMLVideoElement;

let canvases: HTMLCanvasElement[];
let getImageData: ReturnType<typeof vi.fn>;
let drawImage: ReturnType<typeof vi.fn>;

beforeEach(() => {
  canvases = [];
  const realCreate = document.createElement.bind(document);
  vi.spyOn(document, 'createElement').mockImplementation(((tag: string, options?: ElementCreationOptions) => {
    const el = realCreate(tag, options);
    if (tag === 'canvas') canvases.push(el as HTMLCanvasElement);
    return el;
  }) as typeof document.createElement);
  getImageData = vi.fn();
  drawImage = vi.fn();
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation((() => ({
    drawImage,
    getImageData,
  })) as unknown as HTMLCanvasElement['getContext']);
});

afterEach(() => vi.restoreAllMocks());

describe('captureAndAnalyze', () => {
  it('얼굴없음은_faces0_measurement_null이고_캔버스를_비운다', () => {
    const detector: FaceLandmarkDetector = { detect: () => ({ landmarks: null }), close: vi.fn() };
    const r = captureAndAnalyze(video, detector);
    expect(r).toEqual({ faces: 0, measurement: null });
    expect(getImageData).not.toHaveBeenCalled();
    expect(canvases).toHaveLength(1);
    expect(canvases[0].width).toBe(0);
  });

  it('얼굴있음은_ROI_4개를_순서대로_읽고_measure한다', () => {
    getImageData
      .mockReturnValueOnce(solid(255, 0, 0))   // forehead
      .mockReturnValueOnce(solid(0, 255, 0))   // cheekLeft
      .mockReturnValueOnce(solid(0, 255, 0))   // cheekRight
      .mockReturnValueOnce(solid(0, 0, 255));  // nose
    const detector: FaceLandmarkDetector = { detect: () => ({ landmarks: faceLandmarks() }), close: vi.fn() };

    const r = captureAndAnalyze(video, detector);

    expect(r.faces).toBe(1);
    const m = r.measurement!;
    expect(m.rois.forehead.meanA).toBeGreaterThan(60);
    expect(m.rois.nose.meanB).toBeLessThan(-100);
    expect(Math.abs(m.faceWidthRatio - 0.4)).toBeLessThan(1e-9);
    expect(Math.abs(m.noseCenterRatio - 0.5)).toBeLessThan(1e-9);
    expect(m.clippedRatio).toBe(0);
    expect(getImageData).toHaveBeenCalledTimes(4);
    expect(drawImage).toHaveBeenCalledWith(video, 0, 0);
    expect(canvases[0].width).toBe(0);
  });

  it('가로_프레임은_짧은변_기준으로_얼굴폭을_잰다', () => {
    // 16:9 웹캠에서 윤곽선을 채운 얼굴이 프레임 폭 대비로는 작게 잡혀 face-too-small 로 거부되던 결함(설계 6.2 정정).
    getImageData.mockReturnValue(solid(200, 150, 125));
    const lm = faceLandmarks();
    lm[400] = { x: 0.35, y: 0.5 };
    lm[401] = { x: 0.65, y: 0.5 };
    lm[234] = { x: 0.35, y: 0.5 };
    lm[454] = { x: 0.65, y: 0.5 };
    const detector: FaceLandmarkDetector = { detect: () => ({ landmarks: lm }), close: vi.fn() };
    const wide = { videoWidth: 1280, videoHeight: 720 } as HTMLVideoElement;

    const m = captureAndAnalyze(wide, detector).measurement!;

    expect(Math.abs(m.faceWidthRatio - (0.3 * 1280) / 720)).toBeLessThan(1e-9);
  });

  it('detect가_던지면_그대로_던진다', () => {
    const detector: FaceLandmarkDetector = {
      detect: () => { throw new Error('boom'); },
      close: vi.fn(),
    };
    expect(() => captureAndAnalyze(video, detector)).toThrow('boom');
  });
});

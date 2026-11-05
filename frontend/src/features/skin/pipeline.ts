import { clampBox, faceWidthRatio, noseCenterRatio, roiBox } from './roi';
import { roiStats } from './lab';
import { measure } from './metrics';
import { ROI_LANDMARKS, ROI_ORDER, type AnalyzeResult, type FaceLandmarkDetector, type RoiKey, type RoiStats } from './types';

/**
 * video 의 현재 프레임으로 캔버스를 만들어 검출·ROI 읽기·measure 까지 하고 캔버스를 비운다. 동기 함수다.
 * 호출 시점에 video 의 트랙이 살아 있어야 한다(stop 뒤에는 프레임이 검정이거나 0×0 이다).
 * detect 나 getImageData 가 던지면 그대로 던진다(훅이 'analyze-failed' 로 받는다).
 */
export function captureAndAnalyze(video: HTMLVideoElement, detector: FaceLandmarkDetector): AnalyzeResult {
  const canvas = document.createElement('canvas');
  const width = video.videoWidth;
  const height = video.videoHeight;
  canvas.width = width;
  canvas.height = height;
  try {
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    if (!ctx) throw new Error('canvas-context-unavailable');
    ctx.drawImage(video, 0, 0);

    const { landmarks } = detector.detect(canvas);
    if (landmarks === null) return { faces: 0, measurement: null };

    const rois = {} as Record<RoiKey, RoiStats>;
    let clippedRatio = 0;
    for (const key of ROI_ORDER) {
      const clamped = clampBox(roiBox(landmarks, ROI_LANDMARKS[key], width, height), width, height);
      clippedRatio = Math.max(clippedRatio, clamped.clippedRatio);
      const { x, y, w, h } = clamped.box;
      rois[key] = roiStats(ctx.getImageData(x, y, w, h).data);
    }

    const measurement = measure(rois, {
      faceWidthRatio: faceWidthRatio(landmarks),
      noseCenterRatio: noseCenterRatio(landmarks),
      clippedRatio,
    });
    return { faces: 1, measurement };
  } finally {
    canvas.width = 0;
  }
}

import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';
import {
  FACE_LANDMARKER_MODEL_PATH,
  MEDIAPIPE_WASM_PATH,
  MODEL_LOAD_TIMEOUT_MS,
  type FaceLandmarkDetector,
} from './types';

// 진행 중인 생성 promise 와 완성된 검출기를 모듈에 하나씩만 둔다(설계 6.2).
// 타임아웃은 호출자별 창일 뿐 생성을 취소하지 않는다. 늦게 완성된 검출기는 close 하지 않고 캐시에 둔다.
let inflight: Promise<FaceLandmarkDetector> | null = null;
let cached: FaceLandmarkDetector | null = null;

async function build(): Promise<FaceLandmarkDetector> {
  const fileset = await FilesetResolver.forVisionTasks(MEDIAPIPE_WASM_PATH);
  const landmarker = await FaceLandmarker.createFromOptions(fileset, {
    baseOptions: { modelAssetPath: FACE_LANDMARKER_MODEL_PATH, delegate: 'CPU' },
    runningMode: 'IMAGE',
    numFaces: 1,
    outputFacialTransformationMatrixes: false,
  });
  const detector: FaceLandmarkDetector = {
    detect(source) {
      const result = landmarker.detect(source);
      if (result.faceLandmarks.length === 0) return { landmarks: null };
      return { landmarks: result.faceLandmarks[0] };
    },
    close() {
      landmarker.close();
      if (cached === detector) cached = null;
      inflight = null;
    },
  };
  cached = detector;
  return detector;
}

/** 호출자마다 30초 창을 걸고, 못 받으면 그 호출만 reject('model-load-failed'). */
function withTimeout(p: Promise<FaceLandmarkDetector>): Promise<FaceLandmarkDetector> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('model-load-failed')), MODEL_LOAD_TIMEOUT_MS);
    p.then(
      (d) => { clearTimeout(timer); resolve(d); },
      () => { clearTimeout(timer); reject(new Error('model-load-failed')); },
    );
  });
}

// async 함수로 만들면 호출마다 새 Promise 로 감싸져 inflight 를 그대로 넘겨줄 수 없다.
export function createMediaPipeDetector(): Promise<FaceLandmarkDetector> {
  if (cached) return Promise.resolve(cached);
  if (!inflight) {
    const started = build();
    inflight = started;
    // 생성이 실패하면 다음 호출(retry)이 새로 만들 수 있게 비운다.
    started.catch(() => { if (inflight === started) inflight = null; });
  }
  return withTimeout(inflight);
}

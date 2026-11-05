import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';
import {
  FACE_LANDMARKER_MODEL_PATH,
  MEDIAPIPE_WASM_PATH,
  MODEL_LOAD_TIMEOUT_MS,
  type FaceLandmarkDetector,
} from './types';

// 진행 중인 생성 promise 와 완성된 검출기를 모듈에 하나씩만 둔다(설계 6.2).
// 타임아웃은 호출자별 창일 뿐 생성을 취소하지 않는다. 늦게 완성된 검출기는 close 하지 않고 캐시에 둔다.
// 예외(설계 6.2 정정): 어떤 호출자의 30초 창이 만료된 inflight 는 오래됨(stale)으로 표시하고, 그 뒤 새 호출이 오면
// 버리고 새로 생성한다. 생성이 resolve 도 reject 도 없이 멈추면 재사용만으로는 영원히 복구되지 않기 때문이다.
let inflight: Promise<FaceLandmarkDetector> | null = null;
let inflightStale = false;
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
    },
  };
  return detector;
}

/** 생성 하나를 시작해 inflight 로 둔다. 완성되면 캐시에 넣되, 버려진 생성이 늦게 완성됐고 캐시가 이미 있으면 닫고 캐시를 돌려준다. */
function startBuild(): Promise<FaceLandmarkDetector> {
  const started: Promise<FaceLandmarkDetector> = build().then((detector) => {
    if (cached && cached !== detector) {
      detector.close();
      return cached;
    }
    cached = detector;
    return detector;
  });
  inflight = started;
  inflightStale = false;
  // 끝나면(성공·실패) 비운다. 실패 뒤 retry 는 새로 만들고, 성공 뒤에는 캐시가 받는다.
  const settle = () => {
    if (inflight === started) {
      inflight = null;
      inflightStale = false;
    }
  };
  started.then(settle, settle);
  return started;
}

/** 호출자마다 30초 창을 걸고, 못 받으면 그 호출만 reject('model-load-failed'). 만료되면 그 생성을 오래됨으로 표시한다. */
function withTimeout(p: Promise<FaceLandmarkDetector>): Promise<FaceLandmarkDetector> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      if (inflight === p) inflightStale = true;
      reject(new Error('model-load-failed'));
    }, MODEL_LOAD_TIMEOUT_MS);
    p.then(
      (d) => { clearTimeout(timer); resolve(d); },
      () => { clearTimeout(timer); reject(new Error('model-load-failed')); },
    );
  });
}

// async 함수로 만들면 호출마다 새 Promise 로 감싸져 inflight 를 그대로 넘겨줄 수 없다.
export function createMediaPipeDetector(): Promise<FaceLandmarkDetector> {
  if (cached) return Promise.resolve(cached);
  const current = inflight && !inflightStale ? inflight : startBuild();
  return withTimeout(current);
}

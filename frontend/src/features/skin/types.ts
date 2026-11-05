import type { RefObject } from 'react';

// 설계: docs/superpowers/specs/2026-11-05-skin-analysis-design.md 4절·6절.
// 값(GATE, ROI_LANDMARKS, ROI_SHRINK, LM_*)은 설계가 정한 초기값이다. 임계값 확정은 T4 몫이다.

// 4.1 캡처 조건
export const CAMERA_CONSTRAINTS: MediaStreamConstraints = {
  video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } },
  audio: false,
};

// 4.2 ROI 정의
export const ROI_LANDMARKS = {
  forehead:   [67, 109, 10, 338, 297, 69, 108, 151, 337, 299],
  cheekLeft:  [117, 118, 101, 36, 205, 187, 123, 50],     // 이미지 기준 왼쪽
  cheekRight: [346, 347, 330, 266, 425, 411, 352, 280],   // 이미지 기준 오른쪽
  nose:       [6, 197, 195, 5, 4, 45, 275, 168],          // 1차에서는 측정에 쓰지 않는다(피지 2차용)
} as const;
export type RoiKey = keyof typeof ROI_LANDMARKS;
/** pipeline 이 getImageData 를 부르는 순서. 테스트가 이 순서에 기댄다. */
export const ROI_ORDER: readonly RoiKey[] = ['forehead', 'cheekLeft', 'cheekRight', 'nose'];

export interface Box { x: number; y: number; w: number; h: number }
/** 랜드마크 바운딩 박스를 각 변 15% 씩 안으로 줄인다. 경계의 머리카락·눈썹·콧날 그림자를 뺀다. */
export const ROI_SHRINK = 0.15;

/**
 * 좌우 회전(yaw) 대용값. 코끝(1)이 좌우 광대(234, 454) 사이 어디에 있는지 0~1 로 돌려준다.
 * 정면이면 0.5 부근. 고개를 돌리면 한쪽으로 치우친다.
 */
export const LM_NOSE_TIP = 1, LM_CHEEK_EDGE_LEFT = 234, LM_CHEEK_EDGE_RIGHT = 454;

// 4.3 색 변환
/**
 * L* 의 중앙값과 MAD(중앙절대편차)로 컷오프를 잡고, 그 미만인 어두운 픽셀을 평균에서 뺀다. 앞머리·안경테 대책.
 *   cutoff = median(L*) - max(DARK_OUTLIER_MIN_GAP_L, DARK_OUTLIER_K × 1.4826 × MAD)
 */
export const DARK_OUTLIER_K = 3;
export const DARK_OUTLIER_MIN_GAP_L = 8;

export interface RoiStats {
  meanL: number; meanA: number; meanB: number;   // 어두운 이상치를 뺀 절사 평균
  stdL: number;                                  // 절사 전 L* 표준편차. 게이트에 쓰지 않는 진단용(thresholds.md 기록용)
  keptStdL: number;                              // 절사 후 남은 픽셀의 L* 표준편차. 이마 가림 게이트(4.5 MAX_FOREHEAD_KEPT_STD_L)용
  excludedRatio: number;                         // 절사된 픽셀 비율 (0~1)
  pixels: number;                                // 절사 전 픽셀 수
}

// 4.4 지표
export interface Measurement {
  /** 볼 두 곳의 a* 평균에서 이마 a* 를 뺀 값. 유일한 1차 지표. */
  redness: number;
  foreheadL: number;
  foreheadA: number;
  foreheadExcludedRatio: number;  // 'forehead-occluded' 게이트 (4.3 절사 비율)
  cheekA: number;            // (cheekLeft.meanA + cheekRight.meanA) / 2
  faceWidthRatio: number;    // 4.2
  noseCenterRatio: number;   // 4.2
  clippedRatio: number;      // 네 ROI 중 최대 잘림 비율
  rois: Record<RoiKey, RoiStats>;
}

export interface FaceGeometry { faceWidthRatio: number; noseCenterRatio: number; clippedRatio: number }

// 4.5 게이트
export const GATE = {
  /**
   * 얼굴 폭 비율(4.2)이 이 값 미만이면 "더 가까이".
   * 가로 영상에서 윤곽선 폭은 짧은 변의 0.45 다(3:4 크롭 0.75 × 60%). 0.3 이면 얼굴이 윤곽선 폭의
   * 약 2/3 만 채워도 통과한다. 0.4 는 약 89% 를 요구해 데스크톱 웹캠에서 윤곽선 안에 들어와도 거부됐다.
   */
  MIN_FACE_WIDTH_RATIO: 0.3,
  /** ROI 가 프레임 밖으로 잘린 비율이 이 값 초과면 거부. */
  MAX_CLIPPED_RATIO: 0.1,
  /** 코끝 위치 비율(4.2)이 0.5 ± 이 값을 벗어나면 정면이 아님. */
  MAX_NOSE_OFFSET: 0.1,
  /** 이마 L* 가 이 범위 밖이면 조명 부적합. */
  MIN_FOREHEAD_L: 35,
  MAX_FOREHEAD_L: 85,
  /** 이마 ROI 에서 어두운 이상치로 절사된 비율이 이 값 초과면 앞머리·안경테가 크게 덮인 것으로 본다. 소량은 절사가 흡수한다. */
  MAX_FOREHEAD_EXCLUDED: 0.15,
  /** 절사하고 남은 이마 픽셀의 L* 표준편차가 이 값 초과면 가림으로 본다. 가림이 50% 이상이면 중앙값이 머리카락 쪽으로 넘어가 절사가 꺼지므로(excludedRatio 0) 남은 분포가 두 봉우리가 된다. */
  MAX_FOREHEAD_KEPT_STD_L: 6,
  /** 기준 후보 기록과 이마 L* 차이가 이 값을 넘으면 그 기록은 기준에서 뺀다. */
  MAX_FOREHEAD_L_DELTA_FOR_COMPARE: 10,
  /** 붉은기 변화 절대값이 이 값 미만이면 "변화 없음". 11절 공식 2.83σ 의 초기값. */
  REDNESS_NO_CHANGE: 0.4,
  /** 기준값을 만들 때 보는 최근 기록 수. */
  BASELINE_WINDOW: 3,
} as const;

export type GateError =
  | 'no-face' | 'face-out-of-frame' | 'face-too-small' | 'face-not-frontal'
  | 'too-dark' | 'too-bright' | 'forehead-occluded' | 'analyze-failed';

// 4.6 변화 판정
export interface SkinRecord {
  v: 1;
  at: string;            // ISO 8601
  redness: number;
  foreheadL: number;
  foreheadA: number;
  cheekA: number;
  faceWidthRatio: number;
}

export type Verdict =
  | { kind: 'first' }                                            // 이력 없음. 기준값 기록
  | { kind: 'lighting-mismatch'; deltaL: number }                // 최근 3건 모두 조명 불일치. 이번 값은 저장
  | { kind: 'change'; delta: number; baseline: number; label: '증가' | '감소' | '변화 없음' };

// 4.7 프로필 제안 규칙
export const SUGGESTED_CONCERN = 'soothe' as const;

// 6.2 어댑터와 파이프라인 계약
export interface NormalizedLandmark { x: number; y: number; z?: number }
export interface FaceLandmarkDetector {
  /** 얼굴이 없으면 landmarks 는 null. 둘 이상이면 첫 번째. 런타임 예외는 그대로 던진다. */
  detect(source: HTMLCanvasElement): { landmarks: NormalizedLandmark[] | null };
  close(): void;
}
export const MEDIAPIPE_WASM_PATH = '/models/mediapipe/wasm';
export const FACE_LANDMARKER_MODEL_PATH = '/models/mediapipe/face_landmarker.task';
export const MODEL_LOAD_TIMEOUT_MS = 30_000;

export interface AnalyzeResult {
  faces: 0 | 1;
  measurement: Measurement | null;   // faces === 0 이면 null
}

// 6.3 상태 기계
export type CameraError = 'insecure-context' | 'no-camera' | 'permission-denied' | 'camera-busy' | 'camera-failed';
export type ModelError = 'model-load-failed';
export type Phase =
  | 'loading-model' | 'model-error' | 'ready' | 'capturing' | 'analyzing'
  | 'camera-error' | 'gate-error' | 'result';

export interface SkinAnalysisState {
  phase: Phase;
  modelError: ModelError | null;
  cameraError: CameraError | null;
  gateError: GateError | null;
  measurement: Measurement | null;
  verdict: Verdict | null;
  /** 저장하지 않고 매 렌더 suggestConcern(verdict, me) 로 파생한다. ['me'] 가 바뀌면(수락 뒤 무효화) 자연히 null 이 된다. */
  suggestion: 'soothe' | null;
  profileUpdate: 'idle' | 'pending' | 'done' | 'failed';   // result 안의 하위 상태
  meStatus: 'loading' | 'ready' | 'error';                 // ['me'] 쿼리. ready 가 아니면 촬영 비활성, error 면 [다시 시도]=refetch
}
export interface SkinAnalysisActions {
  videoRef: RefObject<HTMLVideoElement | null>;
  capture(): Promise<void>;
  retry(): Promise<void>;
  retryMe(): void;                 // ['me'] refetch
  acceptSuggestion(): Promise<void>;
}

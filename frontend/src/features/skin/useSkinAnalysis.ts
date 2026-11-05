import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchMe, updateProfile } from '../../api/member';
import { useCamera } from './camera';
import { createMediaPipeDetector } from './landmarker';
import { captureAndAnalyze } from './pipeline';
import { gateCapture } from './metrics';
import { judge, suggestConcern, toRecord } from './judge';
import { appendRecord, readHistory } from './history';
import {
  SUGGESTED_CONCERN,
  type AnalyzeResult,
  type FaceLandmarkDetector,
  type GateError,
  type Measurement,
  type ModelError,
  type Phase,
  type SkinAnalysisActions,
  type SkinAnalysisState,
  type Verdict,
} from './types';

declare global {
  interface Window {
    /** 개발 모드 전용. 게이트 판정 전 마지막 측정값(T4 실기기 임계값 확정용). */
    __skinLast?: Measurement | null;
  }
}

type ProfileUpdate = SkinAnalysisState['profileUpdate'];

/** 설계 6.3 상태 기계. 화면은 이 훅의 상태만 그린다. */
export function useSkinAnalysis(): SkinAnalysisState &
  SkinAnalysisActions & {
    /** [촬영] 활성 조건: ready + 회원 정보 로드 + 카메라 켜짐. */
    canCapture: boolean;
  } {
  const { videoRef, status: cameraStatus, error: cameraError, start: startCamera, stop: stopCamera } =
    useCamera();
  const queryClient = useQueryClient();
  const meQuery = useQuery({ queryKey: ['me'], queryFn: fetchMe });

  const [basePhase, setBasePhase] = useState<Phase>('loading-model');
  const [modelError, setModelError] = useState<ModelError | null>(null);
  const [gateError, setGateError] = useState<GateError | null>(null);
  const [measurement, setMeasurement] = useState<Measurement | null>(null);
  const [verdict, setVerdict] = useState<Verdict | null>(null);
  const [profileUpdate, setProfileUpdate] = useState<ProfileUpdate>('idle');

  const detectorRef = useRef<FaceLandmarkDetector | null>(null);
  const mountedRef = useRef(false);
  // 검출기 요청마다 번호를 매겨, 언마운트(또는 StrictMode 첫 마운트) 뒤에 온 결과는 버린다.
  // 버린 검출기는 close 하지 않는다. landmarker 의 공유 캐시라 다음 마운트가 그대로 받는다(설계 6.3 언마운트 행).
  const loadGenerationRef = useRef(0);
  // 숨은 탭에서 검출기가 도착해 카메라 켜기를 미룬 상태.
  const pendingStartRef = useRef(false);

  const loadDetector = useCallback(() => {
    const generation = ++loadGenerationRef.current;
    setModelError(null);
    setBasePhase('loading-model');
    createMediaPipeDetector().then(
      (detector) => {
        if (generation !== loadGenerationRef.current) return;
        detectorRef.current = detector;
        setBasePhase('ready');
        // 카메라는 모델 뒤 직렬. 탭이 숨어 있으면 켜지 않고 다시 보일 때 켠다(설계 6.3 정정).
        if (document.visibilityState === 'hidden') pendingStartRef.current = true;
        else void startCamera();
      },
      () => {
        if (generation !== loadGenerationRef.current) return;
        setModelError('model-load-failed');
        setBasePhase('model-error');
      },
    );
  }, [startCamera]);

  useEffect(() => {
    mountedRef.current = true;
    loadDetector();
    return () => {
      mountedRef.current = false;
      loadGenerationRef.current += 1;
      pendingStartRef.current = false;
      detectorRef.current?.close();
      detectorRef.current = null;
      stopCamera();
    };
  }, [loadDetector, stopCamera]);

  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState !== 'hidden' && pendingStartRef.current) {
        pendingStartRef.current = false;
        void startCamera();
      }
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, [startCamera]);

  const me = meQuery.data;
  const meStatus: SkinAnalysisState['meStatus'] = meQuery.isError ? 'error' : me ? 'ready' : 'loading';

  // 카메라 실패는 ready 위에서만 일어난다. 따로 저장하지 않고 카메라 상태에서 파생해 두 진실을 만들지 않는다.
  const phase: Phase = basePhase === 'ready' && cameraError !== null ? 'camera-error' : basePhase;
  const canCapture = phase === 'ready' && meStatus === 'ready' && cameraStatus === 'live';

  const suggestion = useMemo(
    () =>
      verdict && me ? suggestConcern(verdict, { skinType: me.skinType, concerns: me.concerns }) : null,
    [verdict, me],
  );

  const capture = useCallback(async () => {
    const video = videoRef.current;
    const detector = detectorRef.current;
    if (!canCapture || !video || !detector || !me) return;

    setBasePhase('analyzing');
    video.pause();
    let result: AnalyzeResult;
    try {
      result = captureAndAnalyze(video, detector);
    } catch {
      setGateError('analyze-failed');
      setBasePhase('gate-error');
      return;
    } finally {
      // 순서가 계약이다: 트랙을 먼저 끄면 프레임이 검정이 된다(설계 3절).
      stopCamera();
    }

    if (import.meta.env.DEV) window.__skinLast = result.measurement;

    const gate = gateCapture(result.faces, result.measurement);
    if (gate !== null || result.measurement === null) {
      setGateError(gate ?? 'no-face');
      setBasePhase('gate-error');
      return;
    }
    const m = result.measurement;
    // 판정이 저장보다 먼저다. 반대면 이번 측정이 자기 자신과 비교된다(설계 3절).
    const nextVerdict = judge(m, readHistory(me.id));
    appendRecord(me.id, toRecord(m, new Date()));
    setMeasurement(m);
    setVerdict(nextVerdict);
    setProfileUpdate('idle');
    setBasePhase('result');
  }, [canCapture, me, stopCamera, videoRef]);

  const retry = useCallback(async () => {
    if (basePhase === 'model-error') {
      loadDetector();
      return;
    }
    setGateError(null);
    setMeasurement(null);
    setVerdict(null);
    setProfileUpdate('idle');
    setBasePhase('ready');
    await startCamera();
  }, [basePhase, loadDetector, startCamera]);

  const { refetch } = meQuery;
  const retryMe = useCallback(() => {
    void refetch();
  }, [refetch]);

  const acceptSuggestion = useCallback(async () => {
    setProfileUpdate('pending');
    try {
      // 서버가 세 필드를 통째로 대체하므로 PUT 직전에 최신값을 다시 읽는다(설계 4.7).
      const latest = await queryClient.fetchQuery({ queryKey: ['me'], queryFn: fetchMe, staleTime: 0 });
      if (!latest.concerns.includes(SUGGESTED_CONCERN)) {
        await updateProfile({
          ...(latest.skinType ? { skinType: latest.skinType } : {}),
          concerns: [...latest.concerns, SUGGESTED_CONCERN],
          ...(latest.ageBand ? { ageBand: latest.ageBand } : {}),
        });
      }
      await queryClient.invalidateQueries({ queryKey: ['me'] });
      if (mountedRef.current) setProfileUpdate('done');
    } catch {
      if (mountedRef.current) setProfileUpdate('failed');
    }
  }, [queryClient]);

  return {
    phase,
    modelError,
    cameraError,
    gateError,
    measurement,
    verdict,
    suggestion,
    profileUpdate,
    meStatus,
    canCapture,
    videoRef,
    capture,
    retry,
    retryMe,
    acceptSuggestion,
  };
}

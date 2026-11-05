import { useCallback, useEffect, useRef, useState, type RefObject } from 'react';
import { CAMERA_CONSTRAINTS, type CameraError } from './types';

export type CameraStatus = 'idle' | 'starting' | 'live' | 'stopped' | 'error';

/** 설계 7절 판정 순서의 ③: getUserMedia 예외 이름 → 코드. */
function toCameraError(e: unknown): CameraError {
  const name = e instanceof Error || e instanceof DOMException ? e.name : '';
  if (name === 'NotAllowedError' || name === 'SecurityError') return 'permission-denied';
  if (name === 'NotFoundError') return 'no-camera';
  if (name === 'NotReadableError') return 'camera-busy';
  return 'camera-failed';
}

function stopTracks(stream: MediaStream | null) {
  stream?.getTracks().forEach((t) => t.stop());
}

/**
 * 전면 카메라 스트림 하나를 소유한다. start 가 겹치거나 언마운트 뒤에 스트림이 도착하면
 * 그 스트림은 즉시 정지한다(세대 번호로 판별). 그래야 StrictMode 이중 마운트에서도 스트림이 하나다.
 */
export function useCamera(): {
  videoRef: RefObject<HTMLVideoElement | null>;
  status: CameraStatus;
  error: CameraError | null;
  start(): Promise<void>;
  stop(): void;
} {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const generationRef = useRef(0);
  const [status, setStatus] = useState<CameraStatus>('idle');
  const [error, setError] = useState<CameraError | null>(null);

  const stop = useCallback(() => {
    generationRef.current += 1;
    stopTracks(streamRef.current);
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
    setStatus((s) => (s === 'idle' || s === 'error' ? s : 'stopped'));
  }, []);

  const start = useCallback(async () => {
    const generation = ++generationRef.current;
    const fail = (code: CameraError) => {
      setError(code);
      setStatus('error');
    };
    setError(null);
    setStatus('starting');

    // jsdom 에는 isSecureContext 가 없으므로 undefined 를 통과시킨다(계획 Task 2-2).
    if (window.isSecureContext === false) return fail('insecure-context');
    if (!navigator.mediaDevices?.getUserMedia) return fail('no-camera');

    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia(CAMERA_CONSTRAINTS);
    } catch (e) {
      if (generation === generationRef.current) fail(toCameraError(e));
      return;
    }
    if (generation !== generationRef.current) {
      stopTracks(stream);
      return;
    }
    stopTracks(streamRef.current);
    streamRef.current = stream;
    const video = videoRef.current;
    if (video) {
      video.srcObject = stream;
      // 자동재생 거부는 muted+playsInline 이라 드물다. 실패해도 프레임은 srcObject 로 들어온다.
      await video.play().catch(() => {});
    }
    if (generation === generationRef.current) setStatus('live');
  }, []);

  // 탭이 가려지면 끄고, 켜져 있던 경우만 다시 보일 때 켠다(설계 6.3 마지막 행).
  const resumeRef = useRef(false);
  const statusRef = useRef(status);
  useEffect(() => {
    statusRef.current = status;
  }, [status]);
  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') {
        resumeRef.current = statusRef.current === 'live' || statusRef.current === 'starting';
        if (resumeRef.current) stop();
      } else if (resumeRef.current) {
        resumeRef.current = false;
        void start();
      }
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      document.removeEventListener('visibilitychange', onVisibility);
      stop();
    };
  }, [start, stop]);

  return { videoRef, status, error, start, stop };
}

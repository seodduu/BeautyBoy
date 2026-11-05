import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { useToast } from '../components/ui/useToast';
import { ErrorState } from '../components/common/ErrorState';
import { useSkinAnalysis } from '../features/skin/useSkinAnalysis';
import type { CameraError, GateError, Verdict } from '../features/skin/types';
import './SkinAnalysis.css';

// 문구 톤은 설계 7·8절. 질환·증상명을 쓰지 않고, 절대값에 등급을 붙이지 않는다.
const DISCLAIMER = '의료 진단이 아닙니다. 피부 고민 선택을 돕기 위한 참고 정보입니다.';

const GUIDE_LINES = [
  '창가를 정면으로 마주 보고 서 주세요',
  '윤곽선에 얼굴을 맞춰 주세요',
  '앞머리를 올리고 안경을 벗어 주세요',
  '화장이나 필터 없이 찍어 주세요',
];

const GATE_MESSAGE: Record<GateError, string> = {
  'no-face': '얼굴을 찾지 못했어요. 윤곽선 안에서 정면을 봐 주세요.',
  'face-out-of-frame': '얼굴이 화면 밖으로 나갔어요. 윤곽선 안에 맞춰 주세요.',
  'face-too-small': '얼굴이 너무 작게 찍혔어요. 조금 더 가까이 와 주세요.',
  'face-not-frontal': '고개가 옆으로 돌아가 있어요. 정면을 봐 주세요.',
  'too-dark': '너무 어두워요. 더 밝은 곳에서 찍어 주세요.',
  'too-bright': '빛이 너무 강해요. 역광이나 직사광을 피해 주세요.',
  'forehead-occluded': '이마가 가려져 있어요. 앞머리를 올리고 안경을 벗어 주세요.',
  'analyze-failed': '분석 중 문제가 생겼어요. 다시 찍어 주세요.',
};

const CAMERA_MESSAGE: Record<CameraError, { title: string; description: string; retryable: boolean }> = {
  'insecure-context': {
    title: '보안 연결에서만 카메라를 쓸 수 있어요',
    description: 'HTTPS 주소나 localhost 에서 다시 열어 주세요.',
    retryable: false,
  },
  'no-camera': {
    title: '이 기기에서는 카메라를 쓸 수 없어요',
    description: '카메라가 있는 기기에서 다시 열어 주세요.',
    retryable: false,
  },
  'permission-denied': {
    title: '카메라 권한이 필요해요',
    description: '브라우저 주소창의 사이트 설정에서 카메라를 허용한 뒤 다시 시도해 주세요.',
    retryable: true,
  },
  'camera-busy': {
    title: '다른 앱이 카메라를 쓰고 있어요',
    description: '카메라를 쓰는 앱을 닫은 뒤 다시 시도해 주세요.',
    retryable: true,
  },
  'camera-failed': {
    title: '카메라를 켜지 못했어요',
    description: '잠시 후 다시 시도해 주세요.',
    retryable: true,
  },
};

const CHANGE_WORD = { 증가: '늘었어요', 감소: '줄었어요', '변화 없음': '비슷해요' } as const;

function signed(n: number): string {
  return `${n > 0 ? '+' : ''}${n.toFixed(2)}`;
}

function VerdictBlock({ verdict }: { verdict: Verdict }) {
  if (verdict.kind === 'first') {
    return (
      <div className="bb-skin__verdict">
        <p className="bb-skin__verdict-word">기준값을 기록했어요</p>
        <p className="bb-skin__verdict-note">다음 측정부터 변화를 알려 드려요.</p>
      </div>
    );
  }
  if (verdict.kind === 'lighting-mismatch') {
    return (
      <div className="bb-skin__verdict">
        <p className="bb-skin__verdict-word">최근 측정과 조명이 많이 달라 비교하지 않았어요</p>
        <p className="bb-skin__verdict-note">
          이번 값은 기록해 두었어요. 비슷한 조명에서 다시 찍으면 비교할 수 있어요.
        </p>
      </div>
    );
  }
  return (
    <div className="bb-skin__verdict">
      <p className="bb-skin__verdict-word">최근 측정보다 붉은기가 {CHANGE_WORD[verdict.label]}</p>
      <p className="bb-skin__meta">
        변화 {signed(verdict.delta)} · 기준 {verdict.baseline.toFixed(2)} (최근 측정 중앙값)
      </p>
    </div>
  );
}

/** /skin-analysis. 상태는 useSkinAnalysis(설계 6.3)가 전부 갖고, 이 화면은 phase 별로 그리기만 한다. */
export function SkinAnalysis() {
  const s = useSkinAnalysis();
  const { toast } = useToast();

  // 수락 결과는 토스트 하나로 알린다. 성공하면 ['me'] 갱신으로 제안 블록 자체가 사라지기 때문이다.
  const lastProfileUpdate = useRef(s.profileUpdate);
  useEffect(() => {
    if (lastProfileUpdate.current === s.profileUpdate) return;
    lastProfileUpdate.current = s.profileUpdate;
    if (s.profileUpdate === 'done') toast("프로필 고민에 '진정'을 추가했어요");
    if (s.profileUpdate === 'failed') toast('프로필에 반영하지 못했어요. 다시 시도해 주세요.', { tone: 'danger' });
  }, [s.profileUpdate, toast]);

  const showsCamera = s.phase === 'loading-model' || s.phase === 'ready' || s.phase === 'capturing' || s.phase === 'analyzing';
  const busy = s.phase === 'capturing' || s.phase === 'analyzing';

  return (
    <div className="bb-skin">
      <header className="bb-skin__head">
        <h1 className="bb-skin__title">피부 분석</h1>
        <p className="bb-skin__lede">
          볼과 이마의 붉은기 차이를 재서, 같은 기기의 최근 측정과 비교해 알려 드려요. 사진은 기기 밖으로 나가지 않아요.
        </p>
      </header>

      <div className={`bb-skin__body${showsCamera ? '' : ' bb-skin__body--single'}`}>
        {/* video 는 항상 DOM 에 둔다. 다시 시도할 때 ref 가 비어 있으면 스트림을 붙일 곳이 없다. */}
        <div className="bb-skin__preview" hidden={!showsCamera}>
          <video
            ref={s.videoRef}
            className="bb-skin__video"
            muted
            playsInline
            autoPlay
            aria-label="카메라 미리보기"
          />
          <div className="bb-skin__face-guide" aria-hidden="true" />
          {s.phase === 'loading-model' && (
            <div className="bb-skin__preview-status">
              <span className="bb-skin__spinner" aria-hidden="true" />
            </div>
          )}
        </div>

        <section className="bb-skin__panel" aria-live="polite">
          {s.phase === 'loading-model' && (
            <div role="status">
              <p className="bb-skin__panel-title">분석 모듈을 불러오는 중이에요</p>
              <p className="bb-skin__meta">처음 한 번은 약 18MB 를 내려받아 시간이 걸릴 수 있어요.</p>
            </div>
          )}

          {(s.phase === 'ready' || busy) && (
            <>
              <p className="bb-skin__panel-title">촬영 전에 확인해 주세요</p>
              <ol className="bb-skin__guide">
                {GUIDE_LINES.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ol>
              {s.meStatus === 'error' && (
                <ErrorState
                  title="회원 정보를 불러오지 못했어요"
                  description="측정 기록을 계정별로 남기려면 회원 정보가 필요해요."
                  onRetry={s.retryMe}
                />
              )}
              <div className="bb-skin__actions">
                <Button onClick={() => void s.capture()} disabled={!s.canCapture} loading={busy}>
                  촬영
                </Button>
              </div>
            </>
          )}

          {s.phase === 'model-error' && (
            <ErrorState
              title="분석 모듈을 불러오지 못했어요"
              description="네트워크 연결을 확인한 뒤 다시 시도해 주세요."
              onRetry={() => void s.retry()}
            />
          )}

          {s.phase === 'camera-error' && s.cameraError && (
            <ErrorState
              title={CAMERA_MESSAGE[s.cameraError].title}
              description={CAMERA_MESSAGE[s.cameraError].description}
              onRetry={CAMERA_MESSAGE[s.cameraError].retryable ? () => void s.retry() : undefined}
            />
          )}

          {s.phase === 'gate-error' && s.gateError && (
            <div className="bb-skin__gate" role="alert">
              <p className="bb-skin__panel-title">{GATE_MESSAGE[s.gateError]}</p>
              <p className="bb-skin__meta">이번 촬영은 기록하지 않았어요.</p>
              <div className="bb-skin__actions">
                <Button variant="ghost" onClick={() => void s.retry()}>
                  다시 찍기
                </Button>
              </div>
            </div>
          )}

          {s.phase === 'result' && s.verdict && (
            <>
              <VerdictBlock verdict={s.verdict} />
              {s.suggestion === 'soothe' && (
                <div className="bb-skin__suggest">
                  <p className="bb-skin__suggest-text">
                    프로필 고민에 '진정'을 더해 두면 진정 케어 상품을 추천에 반영해 드려요.
                  </p>
                  <Button
                    variant="ghost"
                    onClick={() => void s.acceptSuggestion()}
                    loading={s.profileUpdate === 'pending'}
                  >
                    프로필 고민에 '진정' 추가
                  </Button>
                </div>
              )}
              <div className="bb-skin__actions">
                <Button variant="ghost" onClick={() => void s.retry()}>
                  다시 측정
                </Button>
                <Link className="bb-btn bb-btn--ghost" to="/mypage/profile">
                  <span className="bb-btn__label">프로필 보기</span>
                </Link>
              </div>
            </>
          )}
        </section>
      </div>

      <p className="bb-skin__disclaimer">{DISCLAIMER}</p>
    </div>
  );
}

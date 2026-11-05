import { StrictMode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { ToastProvider } from '../components/ui/ToastProvider';
import { createMediaPipeDetector } from '../features/skin/landmarker';
import { captureAndAnalyze } from '../features/skin/pipeline';
import { historyKey, readHistory } from '../features/skin/history';
import type { FaceLandmarkDetector, Measurement, RoiStats, SkinRecord } from '../features/skin/types';
import { SkinAnalysis } from './SkinAnalysis';

// 캔버스·MediaPipe 는 jsdom 에서 돌지 않으므로 어댑터와 파이프라인을 통째로 바꾼다(설계 6.1).
vi.mock('../features/skin/landmarker', () => ({ createMediaPipeDetector: vi.fn() }));
vi.mock('../features/skin/pipeline', () => ({ captureAndAnalyze: vi.fn() }));

const MEMBER_ID = 7;
const DISCLAIMER = /의료 진단이 아닙니다/;

function envelope<T>(data: T) {
  return { code: 'OK', message: 'success', data };
}

const okStats: RoiStats = { meanL: 60, meanA: 15, meanB: 12, stdL: 2, keptStdL: 2, excludedRatio: 0, pixels: 1000 };

const OK_MEASUREMENT: Measurement = {
  redness: 1.3,
  foreheadL: 60,
  foreheadA: 15,
  foreheadExcludedRatio: 0,
  cheekA: 16.3,
  faceWidthRatio: 0.5,
  noseCenterRatio: 0.5,
  clippedRatio: 0,
  rois: { forehead: okStats, cheekLeft: okStats, cheekRight: okStats, nose: okStats },
};

function record(overrides: Partial<SkinRecord> = {}): SkinRecord {
  return {
    v: 1,
    at: '2026-11-01T09:00:00.000Z',
    redness: 0.5,
    foreheadL: 60,
    foreheadA: 15,
    cheekA: 15.5,
    faceWidthRatio: 0.5,
    ...overrides,
  };
}

function me(overrides: Record<string, unknown> = {}) {
  return {
    id: MEMBER_ID,
    email: 'dry@beautyboy.dev',
    nickname: '민수',
    grade: 'BASIC',
    skinType: 'OILY',
    concerns: ['sebum'],
    ageBand: '20s',
    ...overrides,
  };
}

let detector: { detect: ReturnType<typeof vi.fn>; close: ReturnType<typeof vi.fn> };
let track: { stop: ReturnType<typeof vi.fn>; kind: string };
let getUserMedia: ReturnType<typeof vi.fn>;
let putBodies: unknown[];

function useMe(body = me()) {
  server.use(http.get('/api/v1/members/me', () => HttpResponse.json(envelope(body))));
}

function renderPage({ strict = false } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const tree = (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/skin-analysis']}>
          <SkinAnalysis />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>
  );
  return render(strict ? <StrictMode>{tree}</StrictMode> : tree);
}

async function captureButtonWhenReady() {
  const button = await screen.findByRole('button', { name: '촬영' });
  await waitFor(() => expect(button).toBeEnabled());
  return button;
}

async function captureWith(measurement: Measurement | null, faces: 0 | 1 = 1) {
  vi.mocked(captureAndAnalyze).mockReturnValue({ faces, measurement });
  fireEvent.click(await captureButtonWhenReady());
}

beforeEach(() => {
  localStorage.clear();
  vi.mocked(captureAndAnalyze).mockReset();

  detector = { detect: vi.fn(), close: vi.fn() };
  vi.mocked(createMediaPipeDetector).mockReset();
  vi.mocked(createMediaPipeDetector).mockResolvedValue(detector as unknown as FaceLandmarkDetector);

  track = { stop: vi.fn(), kind: 'video' };
  const stream = { getTracks: () => [track], getVideoTracks: () => [track] };
  getUserMedia = vi.fn().mockResolvedValue(stream);
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: { getUserMedia },
  });

  vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});

  // ToastProvider 가 prefers-reduced-motion 판정에 matchMedia 를 쓴다(Cart.test.tsx 와 같은 형태).
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));

  putBodies = [];
  useMe();
  server.use(
    http.put('/api/v1/members/me/profile', async ({ request }) => {
      putBodies.push(await request.json());
      return HttpResponse.json(envelope(null));
    }),
  );
});

describe('SkinAnalysis — 피부분석 화면', () => {
  it('모델_로드_뒤_카메라를_켜고_me가_오면_촬영_버튼이_활성화된다', async () => {
    renderPage();

    await captureButtonWhenReady();
    expect(getUserMedia).toHaveBeenCalledTimes(1);
  });

  it('me_조회가_실패하면_촬영은_비활성이고_다시시도가_refetch한다', async () => {
    server.use(http.get('/api/v1/members/me', () => new HttpResponse(null, { status: 500 })));
    renderPage();

    expect(await screen.findByText(/회원 정보를 불러오지 못했어요/)).toBeInTheDocument();
    const capture = screen.getByRole('button', { name: '촬영' });
    expect(capture).toBeDisabled();

    useMe();
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));

    await waitFor(() => expect(capture).toBeEnabled());
  });

  it('권한_거부면_에러와_다시시도가_보이고_이력은_비어있다', async () => {
    getUserMedia.mockRejectedValue(new DOMException('x', 'NotAllowedError'));
    renderPage();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/카메라 권한이 필요해요/);
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
    expect(localStorage.getItem(historyKey(MEMBER_ID))).toBeNull();
  });

  it('다른_앱이_카메라를_쓰면_camera_busy_문구', async () => {
    getUserMedia.mockRejectedValue(new DOMException('x', 'NotReadableError'));
    renderPage();

    expect(await screen.findByText(/다른 앱이 카메라를 쓰고 있어요/)).toBeInTheDocument();
  });

  it('얼굴_미검출이면_이력에_기록하지_않는다', async () => {
    renderPage();
    await captureWith(null, 0);

    expect(await screen.findByText(/얼굴을 찾지 못했어요/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다시 찍기' })).toBeInTheDocument();
    expect(localStorage.getItem(historyKey(MEMBER_ID))).toBeNull();
  });

  it('분석_예외는_analyze_failed로_받고_이력은_없다', async () => {
    renderPage();
    vi.mocked(captureAndAnalyze).mockImplementation(() => {
      throw new Error('x');
    });
    fireEvent.click(await captureButtonWhenReady());

    expect(await screen.findByText(/분석 중 문제가 생겼어요/)).toBeInTheDocument();
    expect(localStorage.getItem(historyKey(MEMBER_ID))).toBeNull();
  });

  it('첫_측정은_기준값_안내를_내고_이력_1건을_남긴다', async () => {
    renderPage();
    await captureWith(OK_MEASUREMENT);

    expect(await screen.findByText(/기준값을 기록했어요/)).toBeInTheDocument();
    expect(readHistory(MEMBER_ID).length).toBe(1);
  });

  it('기준_대비_T_이상_늘면_증가와_제안_버튼이_보인다', async () => {
    localStorage.setItem(historyKey(MEMBER_ID), JSON.stringify([record({ redness: 0.5, foreheadL: 60 })]));
    renderPage();
    await captureWith(OK_MEASUREMENT);

    expect(await screen.findByText(/늘었/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /진정.*추가/ })).toBeInTheDocument();
  });

  it('제안_수락은_최신_me의_세_필드에_soothe를_더해_PUT한다', async () => {
    let current = me();
    server.use(
      http.get('/api/v1/members/me', () => HttpResponse.json(envelope(current))),
      http.put('/api/v1/members/me/profile', async ({ request }) => {
        const body = (await request.json()) as { concerns: string[] };
        putBodies.push(body);
        current = { ...current, concerns: body.concerns };
        return HttpResponse.json(envelope(null));
      }),
    );
    localStorage.setItem(historyKey(MEMBER_ID), JSON.stringify([record()]));
    renderPage();
    await captureWith(OK_MEASUREMENT);

    fireEvent.click(await screen.findByRole('button', { name: /진정.*추가/ }));

    expect(await screen.findByText(/프로필 고민에 '진정'을 추가했어요/)).toBeInTheDocument();
    expect(putBodies).toHaveLength(1);
    expect(putBodies[0]).toEqual({ skinType: 'OILY', concerns: ['sebum', 'soothe'], ageBand: '20s' });
    await waitFor(() => expect(screen.queryByRole('button', { name: /진정.*추가/ })).not.toBeInTheDocument());
  });

  it('다시_읽은_me에_soothe가_이미_있으면_PUT없이_완료', async () => {
    localStorage.setItem(historyKey(MEMBER_ID), JSON.stringify([record()]));
    renderPage();
    await captureWith(OK_MEASUREMENT);
    const accept = await screen.findByRole('button', { name: /진정.*추가/ });

    useMe(me({ concerns: ['sebum', 'soothe'] }));
    fireEvent.click(accept);

    expect(await screen.findByText(/프로필 고민에 '진정'을 추가했어요/)).toBeInTheDocument();
    expect(putBodies).toHaveLength(0);
  });

  it('프로필에_soothe가_있으면_제안이_없다', async () => {
    useMe(me({ concerns: ['soothe'] }));
    localStorage.setItem(historyKey(MEMBER_ID), JSON.stringify([record()]));
    renderPage();
    await captureWith(OK_MEASUREMENT);

    expect(await screen.findByText(/늘었/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /진정.*추가/ })).not.toBeInTheDocument();
  });

  it('SENSITIVE면_제안이_없다', async () => {
    useMe(me({ skinType: 'SENSITIVE' }));
    localStorage.setItem(historyKey(MEMBER_ID), JSON.stringify([record()]));
    renderPage();
    await captureWith(OK_MEASUREMENT);

    expect(await screen.findByText(/늘었/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /진정.*추가/ })).not.toBeInTheDocument();
  });

  it('조명이_D_넘게_다르면_비교하지_않고_이력은_저장한다', async () => {
    localStorage.setItem(historyKey(MEMBER_ID), JSON.stringify([record({ foreheadL: 60 })]));
    renderPage();
    await captureWith({ ...OK_MEASUREMENT, foreheadL: 75 });

    expect(await screen.findByText(/조명이 많이 달라 비교하지 않았어요/)).toBeInTheDocument();
    expect(readHistory(MEMBER_ID).length).toBe(2);
  });

  it('촬영은_captureAndAnalyze_뒤에_트랙을_정지한다', async () => {
    renderPage();
    await captureWith(OK_MEASUREMENT);
    await screen.findByText(/기준값을 기록했어요/);

    expect(track.stop.mock.calls.length).toBeGreaterThanOrEqual(1);
    expect(vi.mocked(captureAndAnalyze).mock.invocationCallOrder[0]).toBeLessThan(
      track.stop.mock.invocationCallOrder[0],
    );
  });

  it('언마운트하면_detector_close와_트랙_stop이_불린다', async () => {
    const { unmount } = renderPage();
    await captureButtonWhenReady();

    unmount();

    expect(detector.close).toHaveBeenCalledTimes(1);
    expect(track.stop).toHaveBeenCalled();
  });

  it('모델_로딩_중_탭이_숨으면_카메라를_켜지_않고_다시_보이면_켠다', async () => {
    // 숨은 탭에서 카메라가 켜지던 결함(설계 6.3 정정).
    let resolveDetector: (d: FaceLandmarkDetector) => void = () => {};
    vi.mocked(createMediaPipeDetector).mockReturnValue(new Promise((res) => { resolveDetector = res; }));
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    renderPage();

    await act(async () => {
      resolveDetector(detector as unknown as FaceLandmarkDetector);
    });
    expect(getUserMedia).not.toHaveBeenCalled();

    visibility.mockReturnValue('visible');
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
    });

    await captureButtonWhenReady();
    expect(getUserMedia).toHaveBeenCalledTimes(1);
    visibility.mockRestore();
  });

  it('StrictMode로_렌더해도_촬영_전에_close가_불리지_않는다', async () => {
    renderPage({ strict: true });

    await captureButtonWhenReady();
    expect(detector.close).not.toHaveBeenCalled();
  });

  it('모든_phase에_의료_진단_아님_문구가_있다', async () => {
    // loading-model: 검출기가 오지 않는다
    vi.mocked(createMediaPipeDetector).mockReturnValue(new Promise(() => {}));
    const loading = renderPage();
    expect(await screen.findByText(/분석 모듈을 불러오는 중/)).toBeInTheDocument();
    expect(screen.getByText(DISCLAIMER)).toBeInTheDocument();
    loading.unmount();

    // ready
    vi.mocked(createMediaPipeDetector).mockResolvedValue(detector as unknown as FaceLandmarkDetector);
    const ready = renderPage();
    await captureButtonWhenReady();
    expect(screen.getByText(DISCLAIMER)).toBeInTheDocument();

    // gate-error
    await captureWith(null, 0);
    await screen.findByText(/얼굴을 찾지 못했어요/);
    expect(screen.getByText(DISCLAIMER)).toBeInTheDocument();
    ready.unmount();

    // result
    renderPage();
    await captureWith(OK_MEASUREMENT);
    await screen.findByText(/기준값을 기록했어요/);
    expect(screen.getByText(DISCLAIMER)).toBeInTheDocument();
  });
});

// 사용자가 확정한 화면(2026-11-05)의 문구·버튼 이름·제목 구조. 문안을 바꾸려면 DESIGN.md 피부분석 절과 함께 고친다.
describe('SkinAnalysis — 확정 화면 구조', () => {
  const CONFIRMED_DISCLAIMER = '의료 진단이 아닙니다. 피부 고민 선택을 돕기 위한 참고 정보입니다.';

  it('가이드_4줄이_확정_문안과_같다', async () => {
    renderPage();
    await captureButtonWhenReady();

    expect(screen.getByRole('heading', { level: 1, name: '피부 분석' })).toBeInTheDocument();
    expect(screen.getByText('촬영 전에 확인해 주세요')).toBeInTheDocument();
    const lines = screen.getAllByRole('listitem').map((li) => li.textContent);
    expect(lines).toEqual([
      '창가를 정면으로 마주 보고 서 주세요',
      '윤곽선에 얼굴을 맞춰 주세요',
      '앞머리를 올리고 안경을 벗어 주세요',
      '화장이나 필터 없이 찍어 주세요',
    ]);
    expect(screen.getByText(CONFIRMED_DISCLAIMER)).toBeInTheDocument();
  });

  it('결과_헤딩과_판정_문구가_확정_문안과_같다', async () => {
    renderPage();
    await captureWith(OK_MEASUREMENT);

    expect(await screen.findByText('기준값을 기록했어요')).toBeInTheDocument();
    expect(screen.getByText('다음 측정부터 변화를 알려 드려요.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: '피부 분석' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다시 측정' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '프로필 보기' })).toHaveAttribute('href', '/mypage/profile');
    expect(screen.getByText(CONFIRMED_DISCLAIMER)).toBeInTheDocument();
  });

  it.each([
    { redness: 1.3, word: '최근 측정보다 붉은기가 늘었어요', meta: '변화 +0.80 · 기준 0.50 (최근 측정 중앙값)' },
    { redness: 0.0, word: '최근 측정보다 붉은기가 줄었어요', meta: '변화 -0.50 · 기준 0.50 (최근 측정 중앙값)' },
    { redness: 0.6, word: '최근 측정보다 붉은기가 비슷해요', meta: '변화 +0.10 · 기준 0.50 (최근 측정 중앙값)' },
  ])('비교_판정은_말이_주이고_숫자는_보조다: $word', async ({ redness, word, meta }) => {
    localStorage.setItem(historyKey(MEMBER_ID), JSON.stringify([record({ redness: 0.5 })]));
    renderPage();
    await captureWith({ ...OK_MEASUREMENT, redness });

    expect(await screen.findByText(word)).toBeInTheDocument();
    expect(screen.getByText(meta)).toBeInTheDocument();
  });

  it('조명_불일치_문구가_확정_문안과_같다', async () => {
    localStorage.setItem(historyKey(MEMBER_ID), JSON.stringify([record({ foreheadL: 60 })]));
    renderPage();
    await captureWith({ ...OK_MEASUREMENT, foreheadL: 75 });

    expect(await screen.findByText('최근 측정과 조명이 많이 달라 비교하지 않았어요')).toBeInTheDocument();
    expect(
      screen.getByText('이번 값은 기록해 두었어요. 비슷한 조명에서 다시 찍으면 비교할 수 있어요.'),
    ).toBeInTheDocument();
  });

  it('제안_버튼_문구가_확정_문안과_같다', async () => {
    localStorage.setItem(historyKey(MEMBER_ID), JSON.stringify([record()]));
    renderPage();
    await captureWith(OK_MEASUREMENT);

    expect(await screen.findByRole('button', { name: "프로필 고민에 '진정' 추가" })).toBeInTheDocument();
    expect(
      screen.getByText("프로필 고민에 '진정'을 더해 두면 진정 케어 상품을 추천에 반영해 드려요."),
    ).toBeInTheDocument();
  });

  it('게이트_실패는_사유와_다시_찍기를_낸다', async () => {
    renderPage();
    await captureWith(null, 0);

    expect(await screen.findByText('얼굴을 찾지 못했어요. 윤곽선 안에서 정면을 봐 주세요.')).toBeInTheDocument();
    expect(screen.getByText('이번 촬영은 기록하지 않았어요.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다시 찍기' })).toBeInTheDocument();
  });
});

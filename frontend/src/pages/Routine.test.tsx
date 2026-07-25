import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { ToastProvider } from '../components/ui/ToastProvider';
import { Routine } from './Routine';
import { QUIZ } from '../components/routine/SkinTypeQuiz';
import * as routineApi from '../api/routine';
import * as compatApi from '../api/compat';
import * as cartApi from '../api/cart';
import type { RoutineResponse, SkinType } from '../api/routine';
import type { CompatCheckResult } from '../api/compat';
import type { GoodsListItem } from '../types/goods';
import { useAuthStore } from '../stores/authStore';
import { clearLocalSkinType, readLocalSkinType } from '../features/routine/skinProfile';

function envelope<T>(data: T) {
  return { code: 'OK', message: 'success', data };
}

const STEP_NAMES = ['클렌징', '토너/스킨', '에센스/세럼', '로션/크림', '선크림'];

function goodsItem(goodsNo: number, name: string): GoodsListItem {
  return {
    goodsNo,
    brandName: '브랜드',
    name,
    thumbnailUrl: 'thumb.png',
    listPrice: 10000,
    salePrice: 9000,
    discountRate: 10,
    badges: [],
    rating: 0,
    reviewCount: 0,
    wished: false,
    todayDreamAvailable: false,
  };
}

function buildRoutine(skinType: SkinType = 'DRY'): RoutineResponse {
  return {
    templateId: 1,
    name: `${skinType} 루틴`,
    skinType,
    time: 'BASIC',
    description: '피부타입에 맞춘 기본 루틴입니다.',
    steps: STEP_NAMES.map((name, index) => ({
      stepOrder: index + 1,
      stepName: name,
      beginnerTip: `${name} 단계 설명`,
      recommendations: [
        goodsItem(index * 10 + 1, `${name} 추천1`),
        goodsItem(index * 10 + 2, `${name} 추천2`),
      ],
    })),
  };
}

const OK_RESULT: CompatCheckResult = { overall: 'OK', findings: [] };

function registerHandlers(
  options: {
    routine?: RoutineResponse;
    compat?: CompatCheckResult;
    meSkinType?: string | null;
  } = {},
) {
  const { routine = buildRoutine(), compat = OK_RESULT, meSkinType } = options;

  server.use(
    http.get('/api/v1/routines', () => HttpResponse.json(envelope(routine))),
    http.post('/api/v1/compat/check', () => HttpResponse.json(envelope(compat))),
    http.post('/api/v1/cart/items/bulk', () => HttpResponse.json(envelope(null), { status: 201 })),
  );

  if (meSkinType !== undefined) {
    server.use(
      http.get('/api/v1/members/me', () =>
        HttpResponse.json(
          envelope({
            id: 1,
            email: 'mock@beautyboy.dev',
            nickname: '민수',
            grade: 'BRONZE',
            skinType: meSkinType,
            concerns: [],
            ageBand: null,
          }),
        ),
      ),
    );
  }
}

function renderRoutine() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/routine']}>
          <Routes>
            <Route path="/routine" element={<Routine />} />
            <Route path="/cart" element={<p>장바구니 화면</p>} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

// ToastProvider가 prefers-reduced-motion 판정에 matchMedia를 쓰므로 jsdom에 최소 구현을 채운다.
beforeEach(() => {
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
  localStorage.clear();
  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: false });
});

afterEach(() => {
  clearLocalSkinType();
  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: false });
  vi.restoreAllMocks();
});

async function answerQuiz(firstOptionOnly = true) {
  for (let i = 0; i < QUIZ.length; i += 1) {
    const question = QUIZ[i];
    const optionIndex = firstOptionOnly ? 0 : 0;
    await screen.findByText(question.question);
    fireEvent.click(screen.getByRole('radio', { name: question.options[optionIndex].label }));
  }
}

describe('Routine — 프로필/퀴즈 진입', () => {
  it('로그인 회원이고 서버 프로필에 skinType이 있으면 퀴즈를 보여주지 않는다', async () => {
    useAuthStore.setState({ accessToken: 'mock-token', member: null, isBootstrapping: false });
    registerHandlers({ meSkinType: 'DRY' });

    renderRoutine();

    expect(await screen.findByRole('heading', { name: /루틴/ })).toBeInTheDocument();
    expect(screen.queryByText(/세수하고 30분/)).not.toBeInTheDocument();
  });

  it('프로필도 로컬 결과도 없으면 퀴즈부터 보여준다', async () => {
    registerHandlers();

    renderRoutine();

    expect(await screen.findByText(/세수하고 30분/)).toBeInTheDocument();
  });

  it('퀴즈를 마치면 결과를 localStorage에 저장하고 그 타입으로 루틴을 조회한다', async () => {
    registerHandlers();
    const fetchRoutineSpy = vi.spyOn(routineApi, 'fetchRoutine');

    renderRoutine();

    // Q1 opt0 DRY+2, Q2 opt0 DRY+1·SENSITIVE+1, Q3 opt0 OILY+1·COMBINATION+1 → 단독 최다 DRY.
    await answerQuiz();

    await waitFor(() => expect(fetchRoutineSpy).toHaveBeenCalledWith('DRY', 'BASIC'));
    expect(readLocalSkinType()).toBe('DRY');
  });
});

describe('Routine — 단계 카드·궁합·전체 담기', () => {
  it('5단계를 순서대로 보여주고 단계마다 추천을 카드로 그린다', async () => {
    registerHandlers();
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: false });
    // 로컬 퀴즈 결과를 미리 심어 퀴즈 화면을 건너뛰고 바로 단계 카드로 간다.
    localStorage.setItem('bb.skinType', 'DRY');

    renderRoutine();

    const steps = await screen.findAllByTestId('routine-step');
    expect(steps).toHaveLength(5);
    expect(within(steps[0]).getByText('클렌징')).toBeInTheDocument();
    expect(within(steps[0]).getAllByTestId('goods-card').length).toBeGreaterThan(0);
  });

  it('단계마다 첫 추천이 기본 선택돼 있다', async () => {
    registerHandlers();
    localStorage.setItem('bb.skinType', 'DRY');

    renderRoutine();

    await screen.findAllByTestId('routine-step');
    expect(await screen.findAllByRole('radio', { checked: true })).toHaveLength(5);
  });

  it('선택을 바꾸면 궁합을 다시 검사한다', async () => {
    registerHandlers();
    localStorage.setItem('bb.skinType', 'DRY');
    const checkCompatSpy = vi.spyOn(compatApi, 'checkCompat');

    renderRoutine();

    await screen.findAllByTestId('routine-step');
    await waitFor(() => expect(checkCompatSpy).toHaveBeenCalled());

    fireEvent.click((await screen.findAllByRole('radio'))[1]);

    await waitFor(() =>
      expect(checkCompatSpy).toHaveBeenLastCalledWith(expect.arrayContaining([expect.any(Number)])),
    );
  });

  it('CONFLICT면 담기 전에 경고를 보여준다', async () => {
    registerHandlers({
      compat: {
        overall: 'CONFLICT',
        findings: [
          {
            verdict: 'CONFLICT',
            categoryA: 'AHA',
            categoryB: '레티노이드',
            reason: '두 성분 모두 자극 중첩 위험이 있어요.',
            goodsNos: [1, 11],
          },
        ],
      },
    });
    localStorage.setItem('bb.skinType', 'DRY');

    renderRoutine();

    expect(await screen.findByRole('alert')).toHaveTextContent(/자극 중첩/);
  });

  it('루틴 전체 담기는 선택된 5개를 한 번에 담고 장바구니로 보낸다', async () => {
    registerHandlers();
    localStorage.setItem('bb.skinType', 'DRY');
    const addCartItemsBulkSpy = vi.spyOn(cartApi, 'addCartItemsBulk');

    renderRoutine();

    await screen.findAllByTestId('routine-step');
    fireEvent.click(await screen.findByRole('button', { name: '루틴 전체 담기' }));

    await waitFor(() =>
      expect(addCartItemsBulkSpy).toHaveBeenCalledWith(
        expect.arrayContaining([expect.objectContaining({ quantity: 1 })]),
      ),
    );
    expect(addCartItemsBulkSpy.mock.calls[0][0]).toHaveLength(5);
    expect(await screen.findByText('장바구니 화면')).toBeInTheDocument();
  });
});

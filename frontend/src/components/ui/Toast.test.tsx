import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { ToastProvider } from './ToastProvider';
import { useToast } from './useToast';

/**
 * useToast().toast(message, opts)를 버튼 클릭으로 트리거하는 최소 하네스.
 */
function Harness({ message, tone }: { message: string; tone?: 'success' | 'danger' }) {
  const { toast } = useToast();
  return (
    <button type="button" onClick={() => toast(message, tone ? { tone } : undefined)}>
      트리거
    </button>
  );
}

function mockMatchMedia(reducedMotion: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: reducedMotion && query.includes('prefers-reduced-motion'),
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

beforeEach(() => {
  vi.useFakeTimers();
  mockMatchMedia(false);
});

afterEach(() => {
  vi.useRealTimers();
});

test('toast 호출 시 role=status, aria-live=polite 라이브리전에 메시지가 노출된다', async () => {
  render(
    <ToastProvider>
      <Harness message="장바구니에 담았어요" />
    </ToastProvider>,
  );

  await act(async () => {
    screen.getByText('트리거').click();
  });

  const region = screen.getByRole('status');
  expect(region).toHaveAttribute('aria-live', 'polite');
  expect(region).toHaveAttribute('aria-atomic', 'true');
  expect(region).toHaveTextContent('장바구니에 담았어요');
});

test('라이브리전은 토스트가 없어도 항상 DOM에 존재한다', () => {
  render(
    <ToastProvider>
      <Harness message="장바구니에 담았어요" />
    </ToastProvider>,
  );

  const region = screen.getByRole('status');
  expect(region).toBeInTheDocument();
  expect(region).toHaveAttribute('aria-live', 'polite');
});

test('토스트는 약 3.5초 후 자동으로 사라진다', async () => {
  render(
    <ToastProvider>
      <Harness message="장바구니에 담았어요" />
    </ToastProvider>,
  );

  await act(async () => {
    screen.getByText('트리거').click();
  });

  expect(screen.getByRole('status')).toHaveTextContent('장바구니에 담았어요');

  await act(async () => {
    vi.advanceTimersByTime(3500);
  });

  expect(screen.getByRole('status')).not.toHaveTextContent('장바구니에 담았어요');
});

test('prefers-reduced-motion이면 애니메이션 클래스가 붙지 않는다', async () => {
  mockMatchMedia(true);
  render(
    <ToastProvider>
      <Harness message="장바구니에 담았어요" />
    </ToastProvider>,
  );

  await act(async () => {
    screen.getByText('트리거').click();
  });

  const toastEl = screen.getByText('장바구니에 담았어요').closest('.bb-toast');
  expect(toastEl).not.toBeNull();
  expect(toastEl?.className).not.toMatch(/bb-toast--animate/);
});

test('reduced-motion이 아니면 애니메이션 클래스가 붙는다', async () => {
  mockMatchMedia(false);
  render(
    <ToastProvider>
      <Harness message="장바구니에 담았어요" />
    </ToastProvider>,
  );

  await act(async () => {
    screen.getByText('트리거').click();
  });

  const toastEl = screen.getByText('장바구니에 담았어요').closest('.bb-toast');
  expect(toastEl?.className).toMatch(/bb-toast--animate/);
});

test('danger 톤은 danger 수정자 클래스를 적용한다', async () => {
  render(
    <ToastProvider>
      <Harness message="문제가 발생했어요" tone="danger" />
    </ToastProvider>,
  );

  await act(async () => {
    screen.getByText('트리거').click();
  });

  const toastEl = screen.getByText('문제가 발생했어요').closest('.bb-toast');
  expect(toastEl?.className).toMatch(/bb-toast--danger/);
});

test('ToastProvider 밖에서 useToast를 호출하면 에러를 던진다', () => {
  const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
  function Bare() {
    useToast();
    return null;
  }
  expect(() => render(<Bare />)).toThrow();
  consoleError.mockRestore();
});

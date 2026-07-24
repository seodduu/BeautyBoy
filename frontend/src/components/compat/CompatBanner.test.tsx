import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CompatBanner } from './CompatBanner';
import type { CompatCheckResult } from '../../api/compat';

const CONFLICT_RESULT: CompatCheckResult = {
  overall: 'CONFLICT',
  findings: [
    {
      verdict: 'CONFLICT',
      categoryA: 'AHA',
      categoryB: '레티노이드',
      reason: '자극 중첩',
      goodsNos: [1, 2],
    },
  ],
};

const CAUTION_RESULT: CompatCheckResult = {
  overall: 'CAUTION',
  findings: [
    {
      verdict: 'CAUTION',
      categoryA: '비타민C',
      categoryB: '나이아신아마이드',
      reason: '민감 피부는 동시 사용 시 붉어질 수 있어요',
      goodsNos: [3, 4],
    },
  ],
};

const SYNERGY_RESULT: CompatCheckResult = {
  overall: 'SYNERGY',
  findings: [
    {
      verdict: 'SYNERGY',
      categoryA: '히알루론산',
      categoryB: '세라마이드',
      reason: '보습 효과를 서로 보완해요',
      goodsNos: [5, 6],
    },
  ],
};

const OK_RESULT: CompatCheckResult = { overall: 'OK', findings: [] };

describe('CompatBanner', () => {
  it('CONFLICT면 role=alert 배너로 이유를 보여준다', () => {
    render(<CompatBanner result={CONFLICT_RESULT} />);

    const banner = screen.getByRole('alert');
    expect(banner).toHaveTextContent('자극 중첩');
  });

  it('CAUTION이면 주의 톤 문구로 role=alert 배너를 보여준다', () => {
    render(<CompatBanner result={CAUTION_RESULT} />);

    const banner = screen.getByRole('alert');
    expect(banner.className).toContain('bb-compat-banner--caution');
    expect(banner).toHaveTextContent('민감 피부는 동시 사용 시 붉어질 수 있어요');
  });

  it('SYNERGY면 경고가 아니라 긍정 안내 문구를 보여준다', () => {
    render(<CompatBanner result={SYNERGY_RESULT} />);

    const banner = screen.getByRole('alert');
    expect(banner.className).toContain('bb-compat-banner--success');
    expect(banner).toHaveTextContent('함께 쓰면 좋은 조합이에요');
    expect(banner).toHaveTextContent('보습 효과를 서로 보완해요');
  });

  it('OK면 아무것도 렌더하지 않는다', () => {
    const { container } = render(<CompatBanner result={OK_RESULT} />);

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(container).toBeEmptyDOMElement();
  });
});

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

/* 리뷰 1-5 재현: 주의(CAUTION)와 시너지(SYNERGY)가 한 응답에 섞여 내려오는 실제 케이스.
   overall은 최악 verdict(CAUTION)이므로, 분리 없이는 시너지 항목이 주의 박스 안에 나열된다. */
const MIXED_RESULT: CompatCheckResult = {
  overall: 'CAUTION',
  findings: [
    {
      verdict: 'CAUTION',
      categoryA: '비타민C',
      categoryB: '나이아신아마이드',
      reason: '민감 피부는 동시 사용 시 붉어질 수 있어요',
      goodsNos: [3, 4],
    },
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

  it('SYNERGY면 경고가 아니라 role=status 긍정 안내로 보여준다', () => {
    render(<CompatBanner result={SYNERGY_RESULT} />);

    const banner = screen.getByRole('status');
    expect(banner.className).toContain('bb-compat-banner--success');
    expect(banner).toHaveTextContent('함께 쓰면 좋은 조합이에요');
    expect(banner).toHaveTextContent('보습 효과를 서로 보완해요');
  });

  it('주의와 시너지가 섞이면 verdict별 섹션으로 분리해 보여준다', () => {
    render(<CompatBanner result={MIXED_RESULT} />);

    // 주의 섹션(alert)에는 CAUTION 항목만 — 시너지 문구가 경고 박스 안에 섞이면 안 된다
    const cautionBanner = screen.getByRole('alert');
    expect(cautionBanner.className).toContain('bb-compat-banner--caution');
    expect(cautionBanner).toHaveTextContent('민감 피부는 동시 사용 시 붉어질 수 있어요');
    expect(cautionBanner).not.toHaveTextContent('보습 효과를 서로 보완해요');

    // 시너지 섹션(status)은 별도 박스로, 긍정 제목과 함께 나온다
    const synergyBanner = screen.getByRole('status');
    expect(synergyBanner.className).toContain('bb-compat-banner--success');
    expect(synergyBanner).toHaveTextContent('함께 쓰면 좋은 조합이에요');
    expect(synergyBanner).toHaveTextContent('보습 효과를 서로 보완해요');
    expect(synergyBanner).not.toHaveTextContent('민감 피부는 동시 사용 시 붉어질 수 있어요');
  });

  it('OK면 아무것도 렌더하지 않는다', () => {
    const { container } = render(<CompatBanner result={OK_RESULT} />);

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(container).toBeEmptyDOMElement();
  });
});

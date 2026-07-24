import type { CompatCheckResult, CompatVerdict } from '../../api/compat';
import './CompatBanner.css';

interface CompatBannerProps {
  result: CompatCheckResult;
}

const TONE_CLASS: Record<CompatVerdict, string> = {
  CONFLICT: 'bb-compat-banner--danger',
  CAUTION: 'bb-compat-banner--caution',
  SYNERGY: 'bb-compat-banner--success',
};

const HEADING: Record<CompatVerdict, string> = {
  CONFLICT: '함께 담은 상품끼리 성분이 부딪혀요',
  CAUTION: '함께 담은 상품, 같이 쓸 때 주의하세요',
  SYNERGY: '함께 쓰면 좋은 조합이에요',
};

const ICON: Record<CompatVerdict, string> = {
  CONFLICT: '!',
  CAUTION: '!',
  SYNERGY: '✓',
};

/**
 * 궁합 경고 배너 — 설계 8장 "적용 지점 ③"(장바구니).
 * DESIGN.md 규칙: 시그널 색은 배경을 칠하지 않고 좌측 보더 + 아이콘 + 텍스트로만 표현한다.
 * overall === 'OK'면 렌더하지 않는다(호출부에서 미리 걸러도 되지만, 여기서도 한 번 더 막아
 * "OK인데 배너가 뜨는" 사고를 이 컴포넌트 하나로 방지한다).
 * SYNERGY는 경고가 아니라 긍정 안내이지만 같은 role="alert" 구조를 재사용한다 —
 * 궁합이 바뀌었다는 사실 자체를 스크린리더가 즉시 읽어줘야 하기 때문이다.
 * CONFLICT여도 이 컴포넌트는 아무것도 비활성화하지 않는다 — 궁합은 조언이지 금지가 아니다.
 */
export function CompatBanner({ result }: CompatBannerProps) {
  if (result.overall === 'OK') {
    return null;
  }

  const tone = result.overall;

  return (
    <div className={`bb-compat-banner ${TONE_CLASS[tone]}`} role="alert">
      <span className="bb-compat-banner__icon" aria-hidden="true">
        {ICON[tone]}
      </span>
      <div className="bb-compat-banner__body">
        <p className="bb-compat-banner__heading">{HEADING[tone]}</p>
        <ul className="bb-compat-banner__list">
          {result.findings.map((finding, index) => (
            <li key={`${finding.categoryA}-${finding.categoryB}-${index}`} className="bb-compat-banner__item">
              <span className="bb-compat-banner__categories">
                {finding.categoryA} · {finding.categoryB}
              </span>
              <span className="bb-compat-banner__reason">{finding.reason}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
